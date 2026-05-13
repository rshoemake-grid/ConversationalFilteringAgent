package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailSessionFilterUtilsTest {

    @Test
    void stripStorage_removesStockAndStorageAttributeAnySegments() {
        assertThat(RetailSessionFilterUtils.stripStorageAttributeAnyFilters(
                "brands: ANY(\"X\") AND attributes.stockType: ANY(\"AMBIENT\")"))
                .isEqualTo("brands: ANY(\"X\")");
        assertThat(RetailSessionFilterUtils.stripStorageAttributeAnyFilters(
                "attributes.storageType: ANY(\"D\", \"DRY_STORAGE\") AND categories: ANY(\"Rice\")"))
                .isEqualTo("categories: ANY(\"Rice\")");
    }

    @Test
    void stripStorage_nullOrBlank_returnsNull() {
        assertThat(RetailSessionFilterUtils.stripStorageAttributeAnyFilters(null)).isNull();
        assertThat(RetailSessionFilterUtils.stripStorageAttributeAnyFilters("  ")).isNull();
    }

    @Test
    void stripStorage_onlyStorageClauses_returnsNull() {
        assertThat(RetailSessionFilterUtils.stripStorageAttributeAnyFilters(
                "attributes.stockType: ANY(\"S\")"))
                .isNull();
    }
}
