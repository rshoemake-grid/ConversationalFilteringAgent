package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceConfigTest {

    @Test
    void stockTypeFilterUseCanonicalValues_defaultsToTrue() {
        assertThat(new ConversationalCommerceConfig().stockTypeFilterUseCanonicalValues()).isTrue();
    }

    @Test
    void stockTypeFilterUseCanonicalValues_canBeEnabled() {
        var config = new ConversationalCommerceConfig();
        config.setStockTypeFilterUseCanonicalValues(true);
        assertThat(config.stockTypeFilterUseCanonicalValues()).isTrue();
    }

    @Test
    void productSearchPageSize_defaultsTo20() {
        var config = new ConversationalCommerceConfig();
        assertThat(config.productSearchPageSize()).isEqualTo(20);
    }

    @Test
    void productSearchPageSize_usesConfiguredValue() {
        var config = new ConversationalCommerceConfig();
        config.setProductSearchPageSize(50);
        assertThat(config.productSearchPageSize()).isEqualTo(50);
    }

    @Test
    void productSearchPageSize_ignoresInvalidValues() {
        var config = new ConversationalCommerceConfig();
        config.setProductSearchPageSize(0);
        assertThat(config.productSearchPageSize()).isEqualTo(20);
        config.setProductSearchPageSize(-1);
        assertThat(config.productSearchPageSize()).isEqualTo(20);
    }

    @Test
    void initialCatalog_defaultsMatchPoC() {
        var config = new ConversationalCommerceConfig();
        assertThat(config.initialCatalogPageSize()).isEqualTo(100);
        assertThat(config.initialCatalogFetchAllPages()).isTrue();
        assertThat(config.initialCatalogMaxProducts()).isEqualTo(1000);
        assertThat(config.initialCatalogSuppressNextPageToken()).isTrue();
        assertThat(config.initialCatalogParallelPageFetches()).isTrue();
        assertThat(config.initialCatalogMaxConcurrentRequests()).isEqualTo(8);
    }

    @Test
    void initialCatalogPageSize_clampedTo100() {
        var config = new ConversationalCommerceConfig();
        config.setInitialCatalogPageSize(500);
        assertThat(config.initialCatalogPageSize()).isEqualTo(100);
    }
}
