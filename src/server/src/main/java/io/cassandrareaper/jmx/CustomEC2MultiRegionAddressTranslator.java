/*
 *
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cassandrareaper.jmx;

import io.cassandrareaper.ReaperApplicationConfiguration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * AddressTranslator implementation for a multi-region EC2 deployment <b>where clients are also deployed in EC2</b>.
 * <p/>
 * Its distinctive feature is that it translates addresses according to the location of the Cassandra host:
 * <ul>
 * <li>addresses in different EC2 regions (than the client) are unchanged;</li>
 * <li>addresses in the same EC2 region are <b>translated to private IPs</b>.</li>
 * </ul>
 * This optimizes network costs, because Amazon charges more for communication over public IPs.
 * <p/>
 * <p/>
 * Implementation note: this class performs a reverse DNS lookup of the origin address,
 * to find the domain name of the target instance. Then it performs a forward DNS lookup of the domain name;
 * the EC2 DNS does the private/public switch automatically based on location.
 * <p/>
 * the code is based on EC2MultiRegionAddressTranslatorTest.java from Datastax java-driver
 */


public class CustomEC2MultiRegionAddressTranslator {

  private static final Logger LOG = LoggerFactory.getLogger(CustomEC2MultiRegionAddressTranslator.class);
  private final DirContext ctx;
  private final ReaperApplicationConfiguration.AddressTranslatorConfiguration addressTranslatorConfiguration;

  public CustomEC2MultiRegionAddressTranslator(
      ReaperApplicationConfiguration.AddressTranslatorConfiguration addressTranslatorConfiguration
  ) throws NamingException {
    Hashtable<Object, Object> env = new Hashtable<Object, Object>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
    this.addressTranslatorConfiguration = addressTranslatorConfiguration;

    ctx = new InitialDirContext(env);
  }

  // Builds the "reversed" domain name in the ARPA domain to perform the reverse lookup
  @VisibleForTesting
  static String reverse(InetAddress address) {
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      return reverseIpv4(bytes);
    } else {
      return reverseIpv6(bytes);
    }
  }

  private static String reverseIpv4(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int i = bytes.length - 1; i >= 0; i--) {
      builder.append(bytes[i] & 0xFF).append('.');
    }
    builder.append("in-addr.arpa");
    return builder.toString();
  }

  private static String reverseIpv6(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int i = bytes.length - 1; i >= 0; i--) {
      byte currentByte = bytes[i];
      int lowNibble = currentByte & 0x0F;
      int highNibble = currentByte >> 4 & 0x0F;
      builder.append(Integer.toHexString(lowNibble)).append('.')
          .append(Integer.toHexString(highNibble)).append('.');
    }
    builder.append("ip6.arpa");
    return builder.toString();
  }

  public InetSocketAddress translate(InetSocketAddress socketAddress) {
    InetAddress address = socketAddress.getAddress();
    try {
      // InetAddress#getHostName() is supposed to perform a reverse DNS lookup, but for some reason it doesn't work
      // within the same EC2 region (it returns the IP address itself).
      // We use an alternate implementation:

      String domainName;
      if (addressTranslatorConfiguration.appendInsteadOfReverse() != null) {
        domainName = lookupPtrRecord(
            address.getHostAddress() + addressTranslatorConfiguration.appendInsteadOfReverse());
      } else {
        domainName = lookupPtrRecord(reverse(address));
      }
      if (domainName == null) {
        LOG.warn("Found no domain name for {}, returning it as-is", address);
        return socketAddress;
      }

      // Waze - remove .pub$ from domain name, by Sasha <sashagl@google.com>

      if (addressTranslatorConfiguration.removeDomain() != null) {
        int sufIndex = domainName.lastIndexOf(addressTranslatorConfiguration.removeDomain());
        if (sufIndex > 0) {
          domainName = domainName.substring(0, sufIndex);
        }
      }


      if (addressTranslatorConfiguration.appendDomain() != null) {
        domainName += addressTranslatorConfiguration.appendDomain();
      }

      InetAddress translatedAddress = InetAddress.getByName(domainName);
      LOG.debug("Resolved {} to {}", address, translatedAddress);
      return new InetSocketAddress(translatedAddress, socketAddress.getPort());
    } catch (IllegalArgumentException
        | SecurityException
        | java.net.UnknownHostException
        | javax.naming.NamingException e) {
      LOG.warn("Error resolving " + address + ", returning it as-is", e);
      return socketAddress;
    }
  }

  private String lookupPtrRecord(String reversedDomain) throws javax.naming.NamingException {
    Attributes attrs = ctx.getAttributes(reversedDomain, new String[]{"PTR"});
    for (NamingEnumeration ae = attrs.getAll(); ae.hasMoreElements(); ) {
      Attribute attr = (Attribute) ae.next();
      for (Enumeration<?> vals = attr.getAll(); vals.hasMoreElements(); ) {
        return vals.nextElement().toString();
      }
    }
    return null;
  }

  public void close() {
    try {
      ctx.close();
    } catch (NamingException e) {
      LOG.warn("Error closing translator", e);
    }
  }

}
