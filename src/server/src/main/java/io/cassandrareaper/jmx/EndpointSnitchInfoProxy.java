/*
 * Copyright 2018-2018 The Last Pickle Ltd
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



import java.net.UnknownHostException;

import com.google.common.base.Preconditions;



public final class EndpointSnitchInfoProxy {

  private final JmxProxyImpl proxy;

  private EndpointSnitchInfoProxy(JmxProxyImpl proxy) {
    this.proxy = proxy;
  }

  public static EndpointSnitchInfoProxy create(JmxProxy proxy) {
    Preconditions.checkArgument(proxy instanceof JmxProxyImpl, "only JmxProxyImpl is supported");
    return new EndpointSnitchInfoProxy((JmxProxyImpl)proxy);
  }


  public String getDataCenter() {
    return getDataCenter(proxy.getUntranslatedHost());
  }

  public String getDataCenter(String host) {
    try {
      return proxy.getEndpointSnitchInfoMBean().getDatacenter(host);
    } catch (UnknownHostException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

}
