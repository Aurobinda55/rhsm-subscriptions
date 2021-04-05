/*
 * Copyright (c) 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.util;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;

/**
 * A simple class that represents a date range.
 */
@Getter
@Setter
public class DateRange {

    private final OffsetDateTime startDate;
    private final OffsetDateTime endDate;

    public DateRange(OffsetDateTime startDate, OffsetDateTime endDate) {
        verifyRange(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static DateRange from(Collection<OffsetDateTime> dates) {
        OffsetDateTime maxTime = null;
        OffsetDateTime minTime = null;
        for (OffsetDateTime next : dates) {
            if (Objects.isNull(maxTime) || next.isAfter(maxTime)) {
                maxTime = next;
            }

            if (Objects.isNull(minTime) || next.isBefore(minTime)) {
                minTime = next;
            }
        }
        return new DateRange(minTime, maxTime);
    }

    private void verifyRange(OffsetDateTime start, OffsetDateTime end) {
        if (!start.equals(end) && start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before end date!");
        }
    }
}
