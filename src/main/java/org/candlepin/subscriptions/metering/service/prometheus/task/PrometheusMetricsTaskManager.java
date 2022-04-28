/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.metering.service.prometheus.task;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusAccountSource;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskDescriptor.TaskDescriptorBuilder;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/** Produces task messages related to pulling metrics back from Telemeter. */
@Component
public class PrometheusMetricsTaskManager {

  private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsTaskManager.class);

  private String topic;

  private TaskQueue queue;

  private PrometheusAccountSource accountSource;

  private TagProfile tagProfile;

  private ApplicationClock clock;

  private ApplicationProperties appProps;

  public PrometheusMetricsTaskManager(
      TaskQueue queue,
      @Qualifier("meteringTaskQueueProperties") TaskQueueProperties queueProps,
      PrometheusAccountSource accountSource,
      TagProfile tagProfile,
      ApplicationClock clock,
      ApplicationProperties appProps) {
    log.info("Initializing metering manager. Topic: {}", queueProps.getTopic());
    this.queue = queue;
    this.topic = queueProps.getTopic();
    this.accountSource = accountSource;
    this.tagProfile = tagProfile;
    this.clock = clock;
    this.appProps = appProps;
  }

  public void updateMetricsForAccount(
      String account, String productTag, OffsetDateTime start, OffsetDateTime end) {
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric -> {
              log.info("Queuing {} {} metric updates for account {}.", productTag, metric, account);
              queueMetricUpdateForAccount(account, productTag, metric, start, end);
              log.info("Done queuing updates of {} {} metric", productTag, metric);
            });
  }

  private void queueMetricUpdateForAccount(
      String account, String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.info(
        "Queuing {} {} metric update for account {} between {} and {}",
        productTag,
        metric,
        account,
        start,
        end);
    this.queue.enqueue(createMetricsTask(account, productTag, metric, start, end));
  }

  @Transactional
  public void updateMetricsForAllAccounts(
      String productTag, OffsetDateTime start, OffsetDateTime end) {
    updateMetricsForAllAccounts(
        productTag, start, end, RetryTemplate.builder().maxAttempts(1).build());
  }

  @Transactional
  public void updateMetricsForAllAccounts(String productTag, int rangeInMinutes) {
    updateMetricsForAllAccounts(
        productTag, rangeInMinutes, RetryTemplate.builder().maxAttempts(1).build());
  }

  @Transactional
  public void updateMetricsForAllAccounts(
      String productTag, int rangeInMinutes, RetryTemplate retry) {
    OffsetDateTime start =
        clock.startOfHour(clock.now().minus(appProps.getPrometheusLatencyDuration()));
    // Minus 1 minute to ensure that we use the last hour's maximum time. If the end
    // time is 6:00:00, taking the last of that hour would give the range an extra hour
    // (6:59:59.999999) which is not what we want. We subtract to break the even boundary before
    // finding the last minute. We need to do this because our queries are date inclusive
    // (greater/less than OR equal to).
    OffsetDateTime end =
        clock.endOfHour(
            start.plusMinutes(rangeInMinutes).truncatedTo(ChronoUnit.HOURS).minusMinutes(1));
    updateMetricsForAllAccounts(productTag, start, end, retry);
  }

  private void updateMetricsForAllAccounts(
      String productTag, OffsetDateTime start, OffsetDateTime end, RetryTemplate retry) {
    log.info("Queuing {} metric updates for range: {} -> {}", productTag, start, end);
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric ->
                retry.execute(
                    context -> {
                      queueMetricUpdateForAllAccounts(productTag, metric, start, end);
                      return null;
                    }));
  }

  private void queueMetricUpdateForAllAccounts(
      String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    try (Stream<String> accountStream =
        accountSource.getMarketplaceAccounts(productTag, metric, end).stream()) {
      log.info("Queuing {} {} metric updates for all configured accounts.", productTag, metric);
      accountStream.forEach(
          account -> queueMetricUpdateForAccount(account, productTag, metric, start, end));
      log.info("Done queuing updates of {} {} metric", productTag, metric);
    }
  }

  private TaskDescriptor createMetricsTask(
      String account, String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.info(
        "ACCOUNT: {} TAG: {} METRIC: {} START: {} END: {}",
        account,
        productTag,
        metric,
        start,
        end);
    TaskDescriptorBuilder builder =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, topic)
            .setSingleValuedArg("account", account)
            .setSingleValuedArg("productTag", productTag)
            .setSingleValuedArg("metric", metric.value())
            .setSingleValuedArg("start", start.toString());

    if (end != null) {
      builder.setSingleValuedArg("end", end.toString());
    }
    return builder.build();
  }
}
