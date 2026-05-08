package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.web.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPoolMapperTest {

    @Test
    void mapsPoolInputToProductResult() {
        var list = List.of(new ChatRequest.ProductPoolInput(
                "p1", "T", "D", "$1", null, null, "sku", List.of("c"), List.of("b"),
                null, null, null, null, Map.of("k", "v"), true));
        var out = ProductPoolMapper.toProductResults(list);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo("p1");
        assertThat(out.get(0).title()).isEqualTo("T");
        assertThat(out.get(0).detailsFetched()).isTrue();
    }
}
