/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.metering.job;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusServicePropeties;
import org.candlepin.subscriptions.metering.service.prometheus.task.PrometheusMetricsTaskManager;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

@ExtendWith(MockitoExtension.class)
public class OpenShiftMeteringJobTest {

    @Mock
    private PrometheusMetricsTaskManager tasks;

    private ApplicationClock clock;
    private PrometheusServicePropeties serviceProps;
    private OpenshiftMeteringJob job;

    @BeforeEach
    void setupTests() {
        serviceProps = new PrometheusServicePropeties();
        serviceProps.setRangeInMinutes(60);

        clock = new FixedClockConfiguration().fixedClock();
        job = new OpenshiftMeteringJob(tasks, clock, serviceProps);
    }

    @Test
    void testRunJob() {
        OffsetDateTime expEndDate = clock.now();
        OffsetDateTime expStartDate = expEndDate.minusMinutes(serviceProps.getRangeInMinutes());
        job.run();

        verify(tasks).updateOpenshiftMetricsForAllAccounts(eq(expStartDate), eq(expEndDate));
    }
}
