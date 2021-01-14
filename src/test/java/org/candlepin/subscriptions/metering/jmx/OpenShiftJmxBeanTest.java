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
package org.candlepin.subscriptions.metering.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
public class OpenShiftJmxBeanTest {

    @Mock
    private PrometheusMetricsTaskManager tasks;

    private ApplicationClock clock;
    private PrometheusServicePropeties serviceProps;
    private OpenshiftJmxBean jmx;

    @BeforeEach
    void setupTests() {
        serviceProps = new PrometheusServicePropeties();
        serviceProps.setRangeInMinutes(60);

        clock = new FixedClockConfiguration().fixedClock();
        jmx = new OpenshiftJmxBean(clock, tasks, serviceProps);
    }

    @Test
    void testMeteringForAccount() {
        String expectedAccount = "test-account";
        OffsetDateTime endDate = clock.now();
        OffsetDateTime startDate = endDate.minusMinutes(serviceProps.getRangeInMinutes());
        jmx.performOpenshiftMeteringForAccount(expectedAccount);

        verify(tasks).updateOpenshiftMetricsForAccount(eq(expectedAccount), eq(startDate), eq(endDate));
    }

    @Test
    void testCustomMeteringForAccount() {
        String expectedAccount = "test-account";
        int rangeInMins = 20;
        OffsetDateTime endDate = clock.now();
        OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
        jmx.performCustomOpenshiftMeteringForAccount(expectedAccount, clock.now().toString(), rangeInMins);

        verify(tasks).updateOpenshiftMetricsForAccount(eq(expectedAccount), eq(startDate), eq(endDate));
    }

    @Test
    void testGetStartDateValidatesRequiredRangeInMinutes() {
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            jmx.performCustomOpenshiftMeteringForAccount("1234", clock.now().toString(), null);
        });
        assertEquals("Required argument: rangeInMinutes", e.getMessage());
    }

    @Test
    void testGetStartDateValidatesRangeInMinutesGreaterEqualToZero() {
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            jmx.performCustomOpenshiftMeteringForAccount("1234", clock.now().toString(), -1);
        });
        assertEquals("Invalid value specified (Must be >= 0): rangeInMinutes", e.getMessage());
    }

    @Test
    void testPerformMeteringForAllAccounts() {
        OffsetDateTime endDate = clock.now();
        OffsetDateTime startDate = endDate.minusMinutes(serviceProps.getRangeInMinutes());
        jmx.performOpenshiftMetering();

        verify(tasks).updateOpenshiftMetricsForAllAccounts(eq(startDate), eq(endDate));
    }

    @Test
    void testPerformCustomMeteringForAllAccounts() {
        int rangeInMins = 20;
        OffsetDateTime endDate = clock.now();
        OffsetDateTime startDate = endDate.minusMinutes(rangeInMins);
        jmx.performCustomOpenshiftMetering(clock.now().toString(), rangeInMins);

        verify(tasks).updateOpenshiftMetricsForAllAccounts(eq(startDate), eq(endDate));
    }
}
