package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEnrichmentServiceTest {

    private static ConversationalCommerceConfig cfg() {
        return new ConversationalCommerceConfig();
    }

    @Test
    void enrich_returnsAsIsWhenProductFetcherEmpty() {
        var service = new ProductEnrichmentService(Optional.empty(), cfg());
        var products = List.of(
                AgentResponse.ProductResult.of("p1", "Product", "", "", null)
        );
        var result = service.enrich(products);
        assertThat(result).isSameAs(products);
    }

    @Test
    void enrich_fetchesAndMergesWhenFieldsMissing() {
        var fetcher = new ProductFetcher() {
            @Override
            public Optional<AgentResponse.ProductResult> getProduct(String productName) {
                if ("projects/p/products/6052075".equals(productName)) {
                    return Optional.of(new AgentResponse.ProductResult(
                            "projects/p/products/6052075",
                            "Medium Grain Rice, 5 pounds",
                            "Long grain rice, 5 lb bag",
                            "$8.99",
                            "https://example.com/rice.png",
                            null,
                            "6052075",
                            List.of("Canned & Dry > Pasta and Rice"),
                            List.of("LA PREF"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            true
                    ));
                }
                return Optional.empty();
            }
        };
        var service = new ProductEnrichmentService(Optional.of(fetcher), cfg());
        var products = List.of(
                new AgentResponse.ProductResult(
                        "projects/p/products/6052075",
                        "Medium Grain Rice, 5 pounds",
                        "",
                        "",
                        "https://mediacdn.sysco.com/images/rice.png",
                        null,
                        null,
                        List.of("Canned & Dry > Pasta and Rice"),
                        List.of("LA PREF"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false
                )
        );
        var result = service.enrich(products);
        assertThat(result).hasSize(1);
        var p = result.get(0);
        assertThat(p.description()).isEqualTo("Long grain rice, 5 lb bag");
        assertThat(p.price()).isEqualTo("$8.99");
        assertThat(p.detailsFetched()).isTrue();
        assertThat(p.imageUri()).isEqualTo("https://mediacdn.sysco.com/images/rice.png");
    }

    @Test
    void enrich_skipsWhenProductHasAllFields() {
        var callCount = new int[1];
        var fetcher = new ProductFetcher() {
            @Override
            public Optional<AgentResponse.ProductResult> getProduct(String productName) {
                callCount[0]++;
                return Optional.empty();
            }
        };
        var service = new ProductEnrichmentService(Optional.of(fetcher), cfg());
        var products = List.of(
                new AgentResponse.ProductResult(
                        "p1",
                        "Full Product",
                        "Has desc",
                        "$10",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        java.util.Map.of("keep", "on-search"),
                        false)
        );
        var result = service.enrich(products);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).detailsFetched()).isFalse();
        assertThat(callCount[0]).isZero();
    }

    @Test
    void enrich_fetchesProductGetWhenAttributesMissingEvenIfDescAndPricePresent() {
        var fetcher = new ProductFetcher() {
            @Override
            public Optional<AgentResponse.ProductResult> getProduct(String productName) {
                if ("projects/p/products/sku-enrich-attrs".equals(productName)) {
                    return Optional.of(new AgentResponse.ProductResult(
                            productName,
                            "T",
                            "from get",
                            "$9",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            java.util.Map.of("stockType", "AMBIENT"),
                            true
                    ));
                }
                return Optional.empty();
            }
        };
        var service = new ProductEnrichmentService(Optional.of(fetcher), cfg());
        var products = List.of(
                new AgentResponse.ProductResult(
                        "projects/p/products/sku-enrich-attrs",
                        "T",
                        "Has desc",
                        "$9",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false)
        );
        var result = service.enrich(products);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).attributes()).containsEntry("stockType", "AMBIENT");
        assertThat(result.get(0).detailsFetched()).isTrue();
        assertThat(result.get(0).description()).isEqualTo("Has desc");
    }

    @Test
    void enrich_preservesOrderWhenMultipleNeedEnrichment() {
        var fetcher = new ProductFetcher() {
            @Override
            public Optional<AgentResponse.ProductResult> getProduct(String productName) {
                return Optional.of(new AgentResponse.ProductResult(
                        productName, "enriched", "d", "$1", null, null, null, null, null, null, null, null, null, null, true));
            }
        };
        var service = new ProductEnrichmentService(Optional.of(fetcher), cfg());
        var products = List.of(
                new AgentResponse.ProductResult("projects/x/branches/b/products/id-a", "t", "", "", null, null, null, null, null, null, null, null, null, null, false),
                new AgentResponse.ProductResult("projects/x/branches/b/products/id-b", "t", "", "", null, null, null, null, null, null, null, null, null, null, false),
                new AgentResponse.ProductResult("projects/x/branches/b/products/id-c", "t", "", "", null, null, null, null, null, null, null, null, null, null, false)
        );
        var result = service.enrich(products);
        assertThat(result).extracting(AgentResponse.ProductResult::id).containsExactly(
                "projects/x/branches/b/products/id-a",
                "projects/x/branches/b/products/id-b",
                "projects/x/branches/b/products/id-c");
        assertThat(result).extracting(AgentResponse.ProductResult::detailsFetched).containsExactly(true, true, true);
        assertThat(result).extracting(AgentResponse.ProductResult::description).containsExactly("d", "d", "d");
    }
}
