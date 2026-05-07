package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiRankingConfigTest {

    @Test
    void model_defaultsToSemanticRankerLatest() {
        var config = new VertexAiRankingConfig();
        assertThat(config.model()).isEqualTo("semantic-ranker-default@latest");
    }

    @Test
    void model_usesPinnedValueWhenSet() {
        var config = new VertexAiRankingConfig();
        config.setModel("semantic-ranker-default-004");
        assertThat(config.model()).isEqualTo("semantic-ranker-default-004");
    }

    @Test
    void model_fallsBackToDefaultWhenBlank() {
        var config = new VertexAiRankingConfig();
        config.setModel("  ");
        assertThat(config.model()).isEqualTo("semantic-ranker-default@latest");
    }
}
