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
package org.candlepin.insights.orgsync.db;

import org.candlepin.insights.orgsync.OrgListStrategy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Pulls the list of orgs to sync from a database table.
 *
 * See {@link Organization}.
 */
public class DatabaseOrgListStrategy implements OrgListStrategy {

    private final OrganizationRepository repo;

    public DatabaseOrgListStrategy(OrganizationRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<String> getOrgsToSync() {
        return repo.findAll().stream().map(Organization::getId).collect(Collectors.toList());
    }
}
