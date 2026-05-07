package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEnrichmentServiceTest {

    @Test
    void enrich_returnsAsIsWhenProductFetcherEmpty() {
        var service = new ProductEnrichmentService(Optional.empty());
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
        var service = new ProductEnrichmentService(Optional.of(fetcher));
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
        var service = new ProductEnrichmentService(Optional.of(fetcher));
        var products = List.of(
                AgentResponse.ProductResult.of("p1", "Full Product", "Has desc", "$10", null)
        );
        var result = service.enrich(products);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).detailsFetched()).isFalse();
        assertThat(callCount[0]).isZero();
    }
}
