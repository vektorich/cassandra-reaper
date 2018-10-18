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

package io.cassandrareaper.service;

import io.cassandrareaper.AppContext;
import io.cassandrareaper.ReaperException;
import io.cassandrareaper.core.DroppedMessages;
import io.cassandrareaper.core.JmxStat;
import io.cassandrareaper.core.MetricsHistogram;
import io.cassandrareaper.core.Node;
import io.cassandrareaper.core.ThreadPoolStat;
import io.cassandrareaper.jmx.MetricsProxy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.management.JMException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetricsService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricsService.class);

  private final AppContext context;

  private MetricsService(AppContext context) {
    this.context = context;
  }

  public static MetricsService create(AppContext context) {
    return new MetricsService(context);
  }

  public List<ThreadPoolStat> getTpStats(Node host) throws ReaperException {
    try {
      int jmxTimeout = context.config.getJmxConnectionTimeoutInSeconds();
      MetricsProxy proxy = MetricsProxy.create(context.jmxConnectionFactory.connect(host, jmxTimeout));
      return convertToThreadPoolStats(proxy.collectTpStats());
    } catch (JMException | InterruptedException | IOException e) {
      LOG.error("Failed collecting tpstats for host {}", host, e);
      throw new ReaperException(e);
    }
  }

  @VisibleForTesting
  public List<ThreadPoolStat> convertToThreadPoolStats(Map<String, List<JmxStat>> jmxStats) {
    List<ThreadPoolStat> tpstats = Lists.newArrayList();
    for (Entry<String, List<JmxStat>> pool : jmxStats.entrySet()) {
      ThreadPoolStat.Builder builder = ThreadPoolStat.builder().withName(pool.getKey());
      for (JmxStat stat : pool.getValue()) {
        builder = updateJmxStatAttribute(stat, builder);
      }
      tpstats.add(builder.build());
    }
    return tpstats;
  }


  public List<DroppedMessages> getDroppedMessages(Node host) throws ReaperException {
    try {
      int jmxTimeout = context.config.getJmxConnectionTimeoutInSeconds();
      MetricsProxy proxy = MetricsProxy.create(context.jmxConnectionFactory.connect(host, jmxTimeout));
      return convertToDroppedMessages(proxy.collectDroppedMessages());
    } catch (JMException | InterruptedException | IOException e) {
      LOG.error("Failed collecting tpstats for host {}", host, e);
      throw new ReaperException(e);
    }
  }

  @VisibleForTesting
  public List<DroppedMessages> convertToDroppedMessages(Map<String, List<JmxStat>> jmxStats) {
    List<DroppedMessages> droppedMessages = Lists.newArrayList();
    for (Entry<String, List<JmxStat>> pool : jmxStats.entrySet()) {
      DroppedMessages.Builder builder = DroppedMessages.builder().withName(pool.getKey());
      for (JmxStat stat : pool.getValue()) {
        builder = updateJmxStatAttribute(stat, builder);
      }
      droppedMessages.add(builder.build());
    }
    return droppedMessages;
  }


  public List<MetricsHistogram> getClientRequestLatencies(Node host) throws ReaperException {
    try {
      int jmxTimeout = context.config.getJmxConnectionTimeoutInSeconds();
      MetricsProxy proxy = MetricsProxy.create(context.jmxConnectionFactory.connect(host, jmxTimeout));
      return convertToMetricsHistogram(proxy.collectLatencyMetrics());
    } catch (JMException | InterruptedException | IOException e) {
      LOG.error("Failed collecting tpstats for host {}", host, e);
      throw new ReaperException(e);
    }
  }

  @VisibleForTesting
  public List<MetricsHistogram> convertToMetricsHistogram(Map<String, List<JmxStat>> jmxStats) {
    List<MetricsHistogram> droppedMessages = Lists.newArrayList();
    for (Entry<String, List<JmxStat>> pool : jmxStats.entrySet()) {
      // We have several metric types that we need to process separately
      // We'll group on MetricsHistogram::getType in order to generate one histogram per type
      Map<String, List<JmxStat>> metrics = pool.getValue().stream().collect(Collectors.groupingBy(JmxStat::getName));

      for (Entry<String, List<JmxStat>> metric : metrics.entrySet()) {
        MetricsHistogram.Builder builder = MetricsHistogram.builder().withName(pool.getKey()).withType(metric.getKey());
        for (JmxStat stat : metric.getValue()) {
          builder = updateJmxStatAttribute(stat, builder);
        }
        droppedMessages.add(builder.build());
      }
    }
    return droppedMessages;
  }

  private static ThreadPoolStat.Builder updateJmxStatAttribute(JmxStat stat, ThreadPoolStat.Builder builder) {
    switch (stat.getName()) {
      case "MaxPoolSize":
        return builder.withMaxPoolSize(stat.getValue().intValue());
      case "TotalBlockedTasks":
        return builder.withTotalBlockedTasks(stat.getValue().intValue());
      case "PendingTasks":
        return builder.withPendingTasks(stat.getValue().intValue());
      case "CurrentlyBlockedTasks":
        return builder.withCurrentlyBlockedTasks(stat.getValue().intValue());
      case "CompletedTasks":
        return builder.withCompletedTasks(stat.getValue().intValue());
      case "ActiveTasks":
        return builder.withActiveTasks(stat.getValue().intValue());
      default:
        return builder;
    }
  }

  private static DroppedMessages.Builder updateJmxStatAttribute(JmxStat stat, DroppedMessages.Builder builder) {
    switch (stat.getAttribute()) {
      case "Count":
        return builder.withCount(stat.getValue().intValue());
      case "OneMinuteRate":
        return builder.withOneMinuteRate(stat.getValue());
      case "FiveMinuteRate":
        return builder.withFiveMinuteRate(stat.getValue());
      case "FifteenMinuteRate":
        return builder.withFifteenMinuteRate(stat.getValue());
      case "MeanRate":
        return builder.withMeanRate(stat.getValue());
      default:
        return builder;
    }
  }

  private static MetricsHistogram.Builder updateJmxStatAttribute(JmxStat stat, MetricsHistogram.Builder builder) {
    switch (stat.getAttribute()) {
      case "Count":
        return builder.withCount(stat.getValue().intValue());
      case "OneMinuteRate":
        return builder.withOneMinuteRate(stat.getValue());
      case "FiveMinuteRate":
        return builder.withFiveMinuteRate(stat.getValue());
      case "FifteenMinuteRate":
        return builder.withFifteenMinuteRate(stat.getValue());
      case "MeanRate":
        return builder.withMeanRate(stat.getValue());
      case "StdDev":
        return builder.withStdDev(stat.getValue());
      case "Min":
        return builder.withMin(stat.getValue());
      case "Max":
        return builder.withMax(stat.getValue());
      case "Mean":
        return builder.withMean(stat.getValue());
      case "50thPercentile":
        return builder.withP50(stat.getValue());
      case "75thPercentile":
        return builder.withP75(stat.getValue());
      case "95thPercentile":
        return builder.withP95(stat.getValue());
      case "98thPercentile":
        return builder.withP98(stat.getValue());
      case "99thPercentile":
        return builder.withP99(stat.getValue());
      case "999thPercentile":
        return builder.withP999(stat.getValue());
      default:
        return builder;
    }
  }

}
