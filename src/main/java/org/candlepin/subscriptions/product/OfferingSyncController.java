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
package org.candlepin.subscriptions.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.subscriptions.capacity.CapacityReconciliationController;
import org.candlepin.subscriptions.capacity.files.ProductWhitelist;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Update {@link Offering}s from product service responses. */
@Component
public class OfferingSyncController {

  private static final Logger LOGGER = LoggerFactory.getLogger(OfferingSyncController.class);

  private static final String SYNC_LOG_TEMPLATE =
      "{} for offeringSku=\"{}\" in offeringSyncTimeMillis={}.";

  private final OfferingRepository offeringRepository;
  private final ProductWhitelist productAllowlist;
  private final ProductService productService;
  private final CapacityReconciliationController capacityReconciliationController;
  private final Timer syncTimer;
  private final Timer enqueueAllTimer;
  private final KafkaTemplate<String, OfferingSyncTask> offeringSyncKafkaTemplate;
  private final ObjectMapper objectMapper;
  private final String offeringSyncTopic;

  @Autowired
  public OfferingSyncController(
      OfferingRepository offeringRepository,
      ProductWhitelist productAllowlist,
      ProductService productService,
      CapacityReconciliationController capacityReconciliationController,
      MeterRegistry meterRegistry,
      KafkaTemplate<String, OfferingSyncTask> offeringSyncKafkaTemplate,
      ObjectMapper objectMapper,
      @Qualifier("offeringSyncTasks") TaskQueueProperties taskQueueProperties) {
    this.offeringRepository = offeringRepository;
    this.productAllowlist = productAllowlist;
    this.productService = productService;
    this.capacityReconciliationController = capacityReconciliationController;
    this.syncTimer = meterRegistry.timer("swatch_offering_sync");
    this.enqueueAllTimer = meterRegistry.timer("swatch_offering_sync_enqueue_all");
    this.offeringSyncKafkaTemplate = offeringSyncKafkaTemplate;
    this.objectMapper = objectMapper;
    this.offeringSyncTopic = taskQueueProperties.getTopic();
  }

  /**
   * Fetches the latest upstream version of an offering and updates Swatch's version if different.
   *
   * @param sku the identifier of the marketing operational product
   */
  public SyncResult syncOffering(String sku) {
    Timer.Sample syncTime = Timer.start();

    if (!productAllowlist.productIdMatches(sku)) {
      SyncResult result = SyncResult.SKIPPED_NOT_ALLOWLISTED;
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      LOGGER.info(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      return result;
    }

    try {
      SyncResult result =
          getUpstreamOffering(sku).map(this::syncOffering).orElse(SyncResult.SKIPPED_NOT_FOUND);
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      LOGGER.info(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      return result;
    } catch (RuntimeException ex) {
      SyncResult result = SyncResult.FAILED;
      Duration syncDuration = Duration.ofNanos(syncTime.stop(syncTimer));
      LOGGER.warn(SYNC_LOG_TEMPLATE, result, sku, syncDuration.toMillis());
      throw ex;
    }
  }

  /**
   * @param sku the identifier of the marketing operational product
   * @return An Offering with information filled by an upstream service, or empty if the product was
   *     not found.
   */
  private Optional<Offering> getUpstreamOffering(String sku) {
    LOGGER.debug("Retrieving product tree for offeringSku=\"{}\"", sku);
    return UpstreamProductData.offeringFromUpstream(sku, productService);
  }

  /**
   * Persists the latest state of an Offering. If no stored Offering matches the SKU, then the
   * Offering is inserted into the datastore. Otherwise, if there are actual changes, the stored
   * Offering with the matching SKU is updated with the given Offering.
   *
   * @param newState the updated Offering
   * @return {@link SyncResult#FETCHED_AND_SYNCED} if upstream offering was stored, or {@link
   *     SyncResult#SKIPPED_MATCHING} if the upstream offering matches what was stored and syncing
   *     was skipped.
   */
  private SyncResult syncOffering(Offering newState) {
    LOGGER.debug("New state of offering to save: {}", newState);
    Optional<Offering> persistedOffering = offeringRepository.findById(newState.getSku());

    if (alreadySynced(persistedOffering, newState)) {
      return SyncResult.SKIPPED_MATCHING;
    }

    // Update to the new entry or create it.
    offeringRepository.saveAndFlush(newState);

    // Existing capacities might need updated if certain parts of the offering was changed.
    capacityReconciliationController.enqueueReconcileCapacityForOffering(newState.getSku());

    return SyncResult.FETCHED_AND_SYNCED;
  }

  /**
   * Enqueues all offerings listed in the product allowlist to be synced with upstream.
   *
   * @return number of enqueued products
   */
  public int syncAllOfferings() {
    Timer.Sample enqueueTime = Timer.start();

    Set<String> products = productAllowlist.allProducts();
    products.forEach(this::enqueueOfferingSyncTask);

    Duration enqueueDuration = Duration.ofNanos(enqueueTime.stop(enqueueAllTimer));
    int numProducts = products.size();
    LOGGER.info(
        "Enqueued numOfferingSyncTasks={} to sync offerings from upstream in enqueueTimeMillis={}",
        numProducts,
        enqueueDuration.toMillis());

    return numProducts;
  }

  // If there is an existing offering in the DB, and it exactly matches the latest upstream
  // version then return true. False means we should sync with the latest upstream version.
  private boolean alreadySynced(Optional<Offering> persisted, Offering latest) {
    return persisted.isPresent() && Objects.equals(persisted.get(), latest);
  }

  private void enqueueOfferingSyncTask(String sku) {
    offeringSyncKafkaTemplate.send(offeringSyncTopic, new OfferingSyncTask(sku));
  }

  @Transactional
  public Stream<String> saveOfferings(
      String offeringsJson, String derivedSkuDataJsonArray, String engProdJsonArray) {
    JsonProductDataSource productDataSource =
        new JsonProductDataSource(
            objectMapper, offeringsJson, derivedSkuDataJsonArray, engProdJsonArray);
    productDataSource
        .getTopLevelSkus()
        .map(sku -> UpstreamProductData.offeringFromUpstream(sku, productDataSource))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(offeringRepository::save);
    return productDataSource.getTopLevelSkus();
  }

  public void deleteOffering(String sku) {
    offeringRepository.deleteById(sku);
  }
}
