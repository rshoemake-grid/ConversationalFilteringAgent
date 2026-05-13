package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StockTypeRetailFilterTest {

    @Test
    void mapsShortCodesToCatalogTokens_whenCanonicalEnabled() {
        var config = new ConversationalCommerceConfig();
        config.setStockTypeFilterUseCanonicalValues(true);
        assertThat(StockTypeRetailFilter.attributeValueForFilter("S", config)).isEqualTo("AMBIENT");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("s", config)).isEqualTo("AMBIENT");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("R", config)).isEqualTo("REFRIGERATED");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("D", config)).isEqualTo("DRY_STORAGE");
    }

    @Test
    void mapsShortCodesToCatalogTokens_withoutConfig_stillUsesCanonicalFallback() {
        assertThat(StockTypeRetailFilter.attributeValueForFilter("S", null)).isEqualTo("AMBIENT");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("R", null)).isEqualTo("REFRIGERATED");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("D", null)).isEqualTo("DRY_STORAGE");
    }

    @Test
    void passesThroughNonRefrigeratedCode() {
        assertThat(StockTypeRetailFilter.attributeValueForFilter("N", null)).isEqualTo("N");
        assertThat(StockTypeRetailFilter.attributeValueForFilter(" n ", null)).isEqualTo("n");
    }

    @Test
    void expansionResolvingToShortLetter_mapsToCatalogToken_whenCanonicalEnabled() {
        var config = new ConversationalCommerceConfig();
        config.setStockTypeFilterUseCanonicalValues(true);
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("Dry storage", "D")));

        assertThat(StockTypeRetailFilter.attributeValueForFilter("Dry storage", config)).isEqualTo("DRY_STORAGE");
    }

    @Test
    void expansionAlreadyCanonical_returnsAsIs_whenCanonicalEnabled() {
        var config = new ConversationalCommerceConfig();
        config.setStockTypeFilterUseCanonicalValues(true);
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("D", "DRY_STORAGE")));

        assertThat(StockTypeRetailFilter.attributeValueForFilter("D", config)).isEqualTo("DRY_STORAGE");
    }

    @Test
    void defaultConfig_mapsShortCodesToCatalogTokens() {
        assertThat(StockTypeRetailFilter.attributeValueForFilter("S", new ConversationalCommerceConfig())).isEqualTo("AMBIENT");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("D", new ConversationalCommerceConfig())).isEqualTo("DRY_STORAGE");
    }

    @Test
    void whenCanonicalDisabled_passesThroughSingleLetterStockCodes() {
        var config = new ConversationalCommerceConfig();
        config.setStockTypeFilterUseCanonicalValues(false);
        assertThat(StockTypeRetailFilter.attributeValueForFilter("S", config)).isEqualTo("S");
        assertThat(StockTypeRetailFilter.attributeValueForFilter("D", config)).isEqualTo("D");
    }

    @Test
    void blankReturnsNull() {
        assertThat(StockTypeRetailFilter.attributeValueForFilter(null, null)).isNull();
        assertThat(StockTypeRetailFilter.attributeValueForFilter("  ", null)).isNull();
    }
}
