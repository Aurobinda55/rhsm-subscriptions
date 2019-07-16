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
package org.candlepin.subscriptions.tally.roller;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.TallyGranularity;
import org.candlepin.subscriptions.util.ApplicationClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Produces monthly usage snapshots based on the existing daily snapshots for the current month.
 */
public class MonthlySnapshotRoller extends BaseSnapshotRoller {

    private static final Logger log = LoggerFactory.getLogger(MonthlySnapshotRoller.class);

    public MonthlySnapshotRoller(String product, TallySnapshotRepository tallyRepo,
        ApplicationClock clock) {
        super(product, tallyRepo, clock);
    }

    @Override
    @Transactional
    public void rollSnapshots(List<String> accounts) {
        log.debug("Producing monthly snapshots for {} account(s).", accounts.size());

        updateSnapshots(accounts, TallyGranularity.DAILY, TallyGranularity.MONTHLY,
            clock.startOfCurrentMonth(), clock.endOfCurrentMonth());
    }

}
