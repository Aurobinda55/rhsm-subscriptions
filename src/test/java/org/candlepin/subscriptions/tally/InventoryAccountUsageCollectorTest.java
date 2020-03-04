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
package org.candlepin.subscriptions.tally;

import static org.candlepin.subscriptions.tally.collector.Assertions.*;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;
import org.candlepin.subscriptions.files.RoleToProductsMapSource;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class InventoryAccountUsageCollectorTest {

    private static final String TEST_PRODUCT = "RHEL";
    private static final Integer TEST_PRODUCT_ID = 1;
    private static final String NON_RHEL = "OTHER PRODUCT";
    private static final Integer NON_RHEL_PRODUCT_ID = 2000;

    private static List<String> rhelProducts = Collections.singletonList(TEST_PRODUCT);
    private static List<String> nonRhelProducts = Collections.singletonList(NON_RHEL);

    @MockBean private BuildProperties buildProperties;
    @MockBean private ClassificationProxyRepository inventoryRepo;
    @Autowired private InventoryAccountUsageCollector collector;

    /**
     * Why are we doing this?  Because when we use a MockBean annotation on the MapSources, we
     * don't get access to the mock until an @BeforeEach method. However, we need to mock the
     * getValue() call before that so the FactNormalizer gets a populated list when it is constructed.
     * The solution is to replace the bean definition of the MapSource with the ones below.
     */
    @TestConfiguration
    static class TestContextConfiguration {
        @Bean
        @Primary
        public ProductIdToProductsMapSource testProductIdToProductsMapSource() throws IOException {
            Map<Integer, List<String>> productList = new HashMap<>();
            productList.put(TEST_PRODUCT_ID, rhelProducts);
            productList.put(NON_RHEL_PRODUCT_ID, nonRhelProducts);

            ProductIdToProductsMapSource source = mock(ProductIdToProductsMapSource.class);
            when(source.getValue()).thenReturn(productList);
            return source;
        }

        @Bean
        @Primary
        public RoleToProductsMapSource testRoleToProducsMapSource() throws IOException {
            RoleToProductsMapSource source = mock(RoleToProductsMapSource.class);
            when(source.getValue()).thenReturn(Collections.emptyMap());
            return source;
        }
    }

    @Test
    public void hypervisorCountsIgnoredForNonRhelProduct() {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts hypervisor = createHypervisor("A1", "O1", NON_RHEL_PRODUCT_ID, 12, 3);

        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(hypervisor).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(nonRhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        // odd sockets are rounded up.
        checkTotalsCalculation(calc, "A1", "O1", NON_RHEL, 12, 4, 1);
        checkPhysicalTotalsCalculation(calc, "A1", "O1", NON_RHEL, 12, 4, 1);
        assertNull(calc.getProductCalculation(NON_RHEL).getTotals(HardwareMeasurementType.HYPERVISOR));
    }

    @Test
    public void hypervisorTotalsForRHEL() {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID, 12, 3);

        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(hypervisor).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        // odd sockets are rounded up.
        checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        assertNull(calc.getProductCalculation(TEST_PRODUCT).getTotals(HardwareMeasurementType.PHYSICAL));
    }

    @Test
    public void guestWithKnownHypervisorNotAddedToTotalsForRHEL() {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts guest = createGuest("hyper-1", "A1", "O1", TEST_PRODUCT_ID, 12, 3);
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(guest).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        ProductUsageCalculation productCalc = calc.getProductCalculation(TEST_PRODUCT);
        assertNull(productCalc.getTotals(HardwareMeasurementType.TOTAL));
        assertNull(productCalc.getTotals(HardwareMeasurementType.PHYSICAL));
        assertNull(productCalc.getTotals(HardwareMeasurementType.HYPERVISOR));
    }

    @Test
    public void guestUnknownHypervisorTotalsForRHEL() {
        List<String> targetAccounts = Arrays.asList("A1");
        ClassifiedInventoryHostFacts guest = createGuest(null, "A1", "O1", TEST_PRODUCT_ID, 12, 3);
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(guest).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 1, 1);
        checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 1, 1);
        assertNull(calc.getProductCalculation(TEST_PRODUCT).getTotals(HardwareMeasurementType.PHYSICAL));
    }

    @Test
    public void physicalSystemTotalsForRHEL() {
        List<String> targetAccounts = Arrays.asList("A1");
        List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

        ClassifiedInventoryHostFacts host = createRhsmHost("A1", "O1", products, 12, 3, "",
            OffsetDateTime.now());
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(host).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        // odd sockets are rounded up.
        checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        checkPhysicalTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        assertNull(calc.getProductCalculation(TEST_PRODUCT).getTotals(HardwareMeasurementType.HYPERVISOR));
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

        ClassifiedInventoryHostFacts host1 = createRhsmHost("A1", "O1", products, 4, 4, "",
            OffsetDateTime.now());
        ClassifiedInventoryHostFacts host2 = createRhsmHost("A1", "O1", products, 8, 4, "",
            OffsetDateTime.now());
        ClassifiedInventoryHostFacts host3 = createRhsmHost("A2", "O2", products, 2, 6, "",
            OffsetDateTime.now());

        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(2, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));
        assertThat(calcs, Matchers.hasKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", 12, 8, 2);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 2, 6, 1);
    }

    @Test
    void testTallyCoresAndSocketsOfRhelViaSystemProfileOnly() throws Exception {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");

        ClassifiedInventoryHostFacts host1 =
            createSystemProfileHost("A1", "O1", Arrays.asList(TEST_PRODUCT_ID), 1, 4, OffsetDateTime.now());
        ClassifiedInventoryHostFacts host2 =
            createSystemProfileHost("A1", "O1", Arrays.asList(TEST_PRODUCT_ID), 2, 4, OffsetDateTime.now());
        ClassifiedInventoryHostFacts host3 =
            createSystemProfileHost("A2", "O2", Arrays.asList(TEST_PRODUCT_ID), 2, 6, OffsetDateTime.now());

        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(2, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));
        assertThat(calcs, Matchers.hasKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        checkTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 12, 6, 1);
    }

    @Test
    public void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts h1 =
            createRhsmHost("A1", "Owner1", Arrays.asList(TEST_PRODUCT_ID), 8, 12, "", OffsetDateTime.now());
        ClassifiedInventoryHostFacts h2 =
            createRhsmHost("A1", "Owner1", Arrays.asList(32), 12, 14, "", OffsetDateTime.now());
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation accountCalc = calcs.get("A1");
        assertEquals(1, accountCalc.getProducts().size());
        checkTotalsCalculation(accountCalc, "A1", "Owner1", TEST_PRODUCT, 8, 12, 1);
    }

    @Test
    public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwnerForSameAccount()
        throws IOException {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts h1 =
            createRhsmHost("A1", "Owner1", Arrays.asList(TEST_PRODUCT_ID), 1, 2, "", OffsetDateTime.now());
        ClassifiedInventoryHostFacts h2 =
            createRhsmHost("A1", "Owner2", Arrays.asList(TEST_PRODUCT_ID), 1, 2, "", OffsetDateTime.now());
        when(inventoryRepo.getFacts(eq(targetAccounts))).thenReturn(Arrays.asList(h1, h2).stream());

        Throwable e = assertThrows(IllegalStateException.class,
            () -> collector.collect(rhelProducts, targetAccounts));

        String expectedMessage = String.format("Attempt to set a different owner for an account: %s:%s",
            "Owner1", "Owner2");
        assertEquals(expectedMessage, e.getMessage());
    }

    @Test
    public void testTallyCoresAndSocketsOfRhelForPhysicalSystems() {
        Collection<String> targetAccounts = Arrays.asList("A1", "A2");
        ClassifiedInventoryHostFacts host1 =
            createRhsmHost("A1", "O1", Arrays.asList(TEST_PRODUCT_ID), 4, 4, "", OffsetDateTime.now());
        ClassifiedInventoryHostFacts host2 = createHypervisor("A1", "O1", TEST_PRODUCT_ID, 8, 4);

        ClassifiedInventoryHostFacts host3 =
            createRhsmHost("A2", "O2", Arrays.asList(TEST_PRODUCT_ID), 2, 6, "", OffsetDateTime.now());
        ClassifiedInventoryHostFacts host4 = createHypervisor("A2", "O2", TEST_PRODUCT_ID, 3, 4);

        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(host1, host2, host3, host4).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(2, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));
        assertThat(calcs, Matchers.hasKey("A2"));

        AccountUsageCalculation a1Calc = calcs.get("A1");
        assertEquals(1, a1Calc.getProducts().size());
        checkTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);
        checkPhysicalTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 4, 4, 1);

        AccountUsageCalculation a2Calc = calcs.get("A2");
        assertEquals(1, a2Calc.getProducts().size());
        checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 5, 10, 2);
        checkPhysicalTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 2, 6, 1);
    }

    @Test
    public void testHypervisorCalculationsWhenMapped() {
        List<String> targetAccounts = Arrays.asList("A1");

        ClassifiedInventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID, 12, 3);

        // Guests should not end up in the total since only the hypervisor should be counted.
        ClassifiedInventoryHostFacts guest1 = createGuest(hypervisor.getSubscriptionManagerId(),
            "A1", "O1", TEST_PRODUCT_ID, 12, 3);

        ClassifiedInventoryHostFacts guest2 = createGuest(hypervisor.getSubscriptionManagerId(),
            "A1", "O1", TEST_PRODUCT_ID, 8, 2);

        when(inventoryRepo.getFacts(eq(targetAccounts)))
            .thenReturn(Arrays.asList(hypervisor, guest1, guest2).stream());

        Map<String, AccountUsageCalculation> calcs = collector.collect(rhelProducts, targetAccounts);
        assertEquals(1, calcs.size());
        assertThat(calcs, Matchers.hasKey("A1"));

        AccountUsageCalculation calc = calcs.get("A1");
        // odd sockets are rounded up for hypervisor.
        checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
        assertNull(calc.getProductCalculation(TEST_PRODUCT).getTotals(HardwareMeasurementType.PHYSICAL));
    }

    private void checkTotalsCalculation(AccountUsageCalculation calc, String account, String owner,
        String product, int cores, int sockets, int instances) {
        assertEquals(account, calc.getAccount());
        assertEquals(owner, calc.getOwner());
        assertTrue(calc.containsProductCalculation(product));

        ProductUsageCalculation prodCalc = calc.getProductCalculation(product);

        assertEquals(product, prodCalc.getProductId());
        assertTotalsCalculation(prodCalc, sockets, cores, instances);
    }

    private void checkPhysicalTotalsCalculation(AccountUsageCalculation calc, String account, String owner,
        String product, int physicalCores, int physicalSockets, int physicalInstances) {
        assertEquals(account, calc.getAccount());
        assertEquals(owner, calc.getOwner());
        assertTrue(calc.containsProductCalculation(product));

        ProductUsageCalculation prodCalc = calc.getProductCalculation(product);
        assertEquals(product, prodCalc.getProductId());
        assertPhysicalTotalsCalculation(prodCalc, physicalSockets, physicalCores,
            physicalInstances);
    }

    private void checkHypervisorTotalsCalculation(AccountUsageCalculation calc, String account,
        String owner, String product, int hypCores, int hypSockets, int hypInstances) {
        assertEquals(account, calc.getAccount());
        assertEquals(owner, calc.getOwner());
        assertTrue(calc.containsProductCalculation(product));

        ProductUsageCalculation prodCalc = calc.getProductCalculation(product);
        assertEquals(product, prodCalc.getProductId());
        assertHypervisorTotalsCalculation(prodCalc, hypSockets, hypCores, hypInstances);
    }
}
