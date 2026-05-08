package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPoolNarrowerTest {

    private final ProductPoolNarrower narrower = new ProductPoolNarrower();

    @Test
    void narrow_keepsProductsMatchingTokens() {
        var pool = List.of(
                AgentResponse.ProductResult.of("1", "Organic Basmati Rice 5lb", "long grain", null, null),
                AgentResponse.ProductResult.of("2", "Iodized Table Salt 26oz", "fine grain", null, null));
        var out = narrower.narrow(pool, "basmati", "", List.of());
        assertThat(out).hasSize(1);
        assertThat(out.get(0).title()).contains("Basmati");
    }

    @Test
    void narrow_returnsEmptyWhenNoTokenMatches() {
        var pool = List.of(AgentResponse.ProductResult.of("1", "Salt", "iodized", null, null));
        var out = narrower.narrow(pool, "chocolate", "", List.of());
        assertThat(out).isEmpty();
    }
}
