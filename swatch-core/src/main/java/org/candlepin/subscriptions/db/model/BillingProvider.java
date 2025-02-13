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
package org.candlepin.subscriptions.db.model;

import java.util.Map;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;

/** Billing provider associated with a host. */
public enum BillingProvider implements StringValueEnum<BillingProviderType> {
  EMPTY("", BillingProviderType.EMPTY),
  RED_HAT("red hat", BillingProviderType.RED_HAT),
  AWS("aws", BillingProviderType.AWS),
  GCP("gcp", BillingProviderType.GCP),
  AZURE("azure", BillingProviderType.AZURE),
  ORACLE("oracle", BillingProviderType.ORACLE),
  _ANY("_ANY", BillingProviderType._ANY); // NOSONAR

  private static final Map<String, BillingProvider> VALUE_ENUM_MAP =
      StringValueEnum.initializeImmutableMap(BillingProvider.class);

  private final String value;
  private final BillingProviderType openApiEnum;

  BillingProvider(String value, BillingProviderType openApiEnum) {
    this.value = value;
    this.openApiEnum = openApiEnum;
  }

  /**
   * Parse the BillingProvider from its string representation
   *
   * @param value String representation of the BillingProvider, as seen in a host record
   * @return the BillingProvider enum
   */
  public static BillingProvider fromString(String value) {
    return StringValueEnum.getValueOf(BillingProvider.class, VALUE_ENUM_MAP, value, EMPTY);
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public BillingProviderType asOpenApiEnum() {
    return openApiEnum;
  }

  /** JPA converter for BillingProvider */
  @Converter(autoApply = true)
  public static class EnumConverter implements AttributeConverter<BillingProvider, String> {

    @Override
    public String convertToDatabaseColumn(BillingProvider attribute) {
      if (attribute == null) {
        return null;
      }
      return attribute.getValue();
    }

    @Override
    public BillingProvider convertToEntityAttribute(String dbData) {
      return BillingProvider.fromString(dbData);
    }
  }
}
