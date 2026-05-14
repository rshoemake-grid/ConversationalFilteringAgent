package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.orchestration.RetailProductApiGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BrandDisplayResolver.
 */
class BrandDisplayResolverTest {

    private ConversationalCommerceConfig config;
    private BrandDisplayResolver resolver;

    @BeforeEach
    void setUp() {
        config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setRetailSingleShotPerConversation(false);
    }

    @Test
    void resolve_returnsConfigMappingWhenPresent() {
        config.setAttributeDisplayMapping(Map.of(
                "brands", Map.of("NIKE", "Nike", "ADIDAS", "Adidas")
        ));
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Nike");
        assertThat(resolver.resolveDisplayText("attributes.brandId", "ADIDAS")).isEqualTo("Adidas");
    }

    @Test
    void resolve_returnsProductBrandFromApiWhenNoConfigAndSearchClientPresent() {
        resolver = new BrandDisplayResolver(config, new StubSearchClient(), new RetailProductApiGate(config));

        // Stub returns product with brands=["Nike"] for filter on NIKE
        String result = resolver.resolveDisplayText("attributes.brands", "NIKE");
        assertThat(result).isEqualTo("Nike");
    }

    @Test
    void resolve_fallsBackToTitleCaseWhenNoConfigAndNoSearchClient() {
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Nike");
        assertThat(resolver.resolveDisplayText("attributes.brandId", "ADIDAS")).isEqualTo("Adidas");
    }

    @Test
    void resolve_fallsBackToTitleCaseWhenSearchReturnsEmpty() {
        resolver = new BrandDisplayResolver(config, new StubSearchClient(), new RetailProductApiGate(config)); // stub returns empty for unknown

        String result = resolver.resolveDisplayText("attributes.brands", "UNKNOWN_BRAND");
        assertThat(result).isEqualTo("Unknown_brand");
    }

    @Test
    void resolve_returnsValueAsIsForNonBrandAttribute() {
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.type", "Balloons")).isEqualTo("Balloons");
        assertThat(resolver.resolveDisplayText("attributes.color", "BLUE")).isEqualTo("BLUE");
    }

    @Test
    void resolve_formatsDoubleUnderscorePackSize() {
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.netWeight", "3__LB")).isEqualTo("3 lb");
        assertThat(resolver.resolveDisplayText("attributes.packSize", "12__OZ")).isEqualTo("12 oz");
        assertThat(resolver.resolveDisplayText(null, "3__LB")).isEqualTo("3 lb");
    }

    @Test
    void resolve_attributeMappingOverridesPackSizeFormat() {
        config.setAttributeDisplayMapping(Map.of(
                "packSize", Map.of("3__LB", "3-pound bag")
        ));
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.packSize", "3__LB")).isEqualTo("3-pound bag");
    }

    @Test
    void resolve_configTakesPrecedenceOverApi() {
        config.setAttributeDisplayMapping(Map.of("brands", Map.of("NIKE", "Custom Nike")));
        resolver = new BrandDisplayResolver(config, new StubSearchClient(), new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Custom Nike");
    }

    @Test
    void resolve_skipsSearchWhenRetailProductGateBlocks() {
        var calls = new AtomicInteger();
        RetailSearchClient client = new RetailSearchClient() {
            @Override
            public SearchResult searchWithPagination(String placement, String branch, String query, String visitorId,
                                                     String filter, String pageToken, Integer pageSizeOverride, Integer offset) {
                calls.incrementAndGet();
                return SearchResult.of(List.of());
            }
        };
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);
        gate.beginChatTurn("cb", "sb");
        gate.noteRetailProductListingCommitted("rice", null);
        gate.endChatTurn();
        gate.beginChatTurn("cb", "sb");
        resolver = new BrandDisplayResolver(config, client, gate);

        assertThat(resolver.resolveDisplayText("attributes.brands", "NIKE")).isEqualTo("Nike");
        assertThat(calls.get()).isZero();
        gate.endChatTurn();
    }

    @Test
    void resolve_storageTypeShortCodesWithoutAttributeName_usesDisplayMapping() {
        config.setAttributeDisplayMapping(Map.of(
                "storageType", Map.of("S", "Ambient", "R", "Refrigerated", "D", "Dry storage")
        ));
        resolver = new BrandDisplayResolver(config, null, new RetailProductApiGate(config));

        assertThat(resolver.resolveDisplayText(null, "S")).isEqualTo("Ambient");
        assertThat(resolver.resolveDisplayText("", "R")).isEqualTo("Refrigerated");
        assertThat(resolver.resolveDisplayText(null, "D")).isEqualTo("Dry storage");
    }

    private static class StubSearchClient implements RetailSearchClient {
        @Override
        public SearchResult searchWithPagination(String placement, String branch, String query, String visitorId, String filter, String pageToken, Integer pageSizeOverride, Integer offset) {
            List<AgentResponse.ProductResult> list = (filter != null && filter.contains("NIKE"))
                    ? List.of(new AgentResponse.ProductResult(
                            null, "Test", null, null, null, null, null,
                            null, List.of("Nike"), null, null, null, null, null, false))
                    : List.of();
            return SearchResult.of(list);
        }
    }
}
