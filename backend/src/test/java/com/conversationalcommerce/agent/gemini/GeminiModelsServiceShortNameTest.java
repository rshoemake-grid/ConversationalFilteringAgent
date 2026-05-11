package com.conversationalcommerce.agent.gemini;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiModelsServiceShortNameTest {

    @Test
    void shortModelName_stripsVertexResourcePath() {
        assertThat(GeminiModelsService.shortModelName(
                "projects/p/locations/us-central1/publishers/google/models/gemini-2.5-flash"))
                .isEqualTo("gemini-2.5-flash");
    }

    @Test
    void shortModelName_stripsModelsPrefix() {
        assertThat(GeminiModelsService.shortModelName("models/gemini-pro"))
                .isEqualTo("gemini-pro");
    }
}
