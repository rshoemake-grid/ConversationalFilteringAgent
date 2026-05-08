package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.web.ChatRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps client-supplied pool entries (from the prior VAISR search) to {@link AgentResponse.ProductResult}.
 */
public final class ProductPoolMapper {

    private ProductPoolMapper() {}

    public static List<AgentResponse.ProductResult> toProductResults(List<ChatRequest.ProductPoolInput> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<AgentResponse.ProductResult>(pool.size());
        for (ChatRequest.ProductPoolInput p : pool) {
            if (p == null) continue;
            out.add(new AgentResponse.ProductResult(
                    emptyToNull(p.id()),
                    emptyToNull(p.title()),
                    emptyToNull(p.description()),
                    emptyToNull(p.price()),
                    emptyToNull(p.imageUri()),
                    emptyToNull(p.gtin()),
                    emptyToNull(p.productId()),
                    nullToEmptyList(p.categories()),
                    nullToEmptyList(p.brands()),
                    emptyToNull(p.uri()),
                    emptyToNull(p.availability()),
                    nullToEmptyList(p.sizes()),
                    nullToEmptyList(p.materials()),
                    p.attributes() != null ? p.attributes() : Map.of(),
                    Boolean.TRUE.equals(p.detailsFetched())
            ));
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private static List<String> nullToEmptyList(List<String> l) {
        return l != null ? l : List.of();
    }
}
