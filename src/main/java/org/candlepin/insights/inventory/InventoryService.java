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
package org.candlepin.insights.inventory;

import org.candlepin.insights.api.model.ConsumerInventory;
import org.candlepin.insights.api.model.OrgInventory;
import org.candlepin.insights.exception.RhsmConduitException;
import org.candlepin.insights.exception.inventory.InventoryServiceException;
import org.candlepin.insights.inventory.client.model.BulkHostOut;
import org.candlepin.insights.inventory.client.model.CreateHostIn;
import org.candlepin.insights.inventory.client.model.FactSet;
import org.candlepin.insights.inventory.client.resources.HostsApi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * A wrapper for the insights inventory client.
 */
@Component
public class InventoryService {

    private final HostsApi hostsInventoryApi;

    @Autowired
    public InventoryService(HostsApi hostsInventoryApi) {
        this.hostsInventoryApi = hostsInventoryApi;
    }

    public BulkHostOut sendHostUpdate(List<ConduitFacts> facts)
        throws RhsmConduitException {

        List<CreateHostIn> hostsToSend = facts.stream()
            .map(this::createHost)
            .collect(Collectors.toList());

        try {
            return hostsInventoryApi.apiHostAddHostList(hostsToSend);
        }
        catch (Exception e) {
            throw new InventoryServiceException(
                "An error occurred while sending a host update to the inventory service.", e);
        }
    }

    /**
     * Given a set of facts, report them as a host to the inventory service.
     *
     * @return the new host.
     */
    private CreateHostIn createHost(ConduitFacts conduitFacts) {
        Map<String, Object> rhsmFactMap = new HashMap<>();
        rhsmFactMap.put("orgId", conduitFacts.getOrgId());
        if (conduitFacts.getCpuSockets() != null) {
            rhsmFactMap.put("CPU_SOCKETS", conduitFacts.getCpuSockets());
        }
        if (conduitFacts.getCpuCores() != null) {
            rhsmFactMap.put("CPU_CORES", conduitFacts.getCpuCores());
        }
        if (conduitFacts.getMemory() != null) {
            rhsmFactMap.put("MEMORY", conduitFacts.getMemory());
        }
        if (conduitFacts.getArchitecture() != null) {
            rhsmFactMap.put("ARCHITECTURE", conduitFacts.getArchitecture());
        }
        if (conduitFacts.getIsVirtual() != null) {
            rhsmFactMap.put("IS_VIRTUAL", conduitFacts.getIsVirtual());
        }
        if (conduitFacts.getVmHost() != null) {
            rhsmFactMap.put("VM_HOST", conduitFacts.getVmHost());
        }
        if (conduitFacts.getRhProd() != null) {
            rhsmFactMap.put("RH_PROD", conduitFacts.getRhProd());
        }

        FactSet rhsmFacts = new FactSet()
            .namespace("rhsm")
            .facts(rhsmFactMap);
        List<FactSet> facts = new LinkedList<>();
        facts.add(rhsmFacts);

        CreateHostIn host = new CreateHostIn();
        host.setAccount(conduitFacts.getAccountNumber());
        host.setFqdn(conduitFacts.getFqdn());
        host.setSubscriptionManagerId(conduitFacts.getSubscriptionManagerId());
        host.setBiosUuid(conduitFacts.getBiosUuid());
        host.setIpAddresses(conduitFacts.getIpAddresses());
        host.setMacAddresses(conduitFacts.getMacAddresses());
        host.facts(facts);
        return host;
    }

    public OrgInventory getInventoryForOrgConsumers(List<ConduitFacts> conduitFactsForOrg) {
        List<ConsumerInventory> hosts = new ArrayList<>(conduitFactsForOrg);
        return new OrgInventory().consumerInventories(hosts);
    }
}
