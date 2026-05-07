package com.conversationalcommerce.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Vertex AI Ranking API (Discovery Engine) — semantic rerank model for pooled search.
 * Endpoint resource: {@code rankingConfigs/default_ranking_config:rank}.
 */
@Configuration
@ConfigurationProperties(prefix = "vertex-ai-ranking")
public class VertexAiRankingConfig {

    private static final String DEFAULT_MODEL = "semantic-ranker-default@latest";

    /** Ranking model sent as {@code model} on rank requests (e.g. semantic-ranker-default@latest). */
    private String model = DEFAULT_MODEL;

    public String model() {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        return model.strip();
    }

    public void setModel(String model) {
        this.model = model != null ? model : DEFAULT_MODEL;
    }
}
