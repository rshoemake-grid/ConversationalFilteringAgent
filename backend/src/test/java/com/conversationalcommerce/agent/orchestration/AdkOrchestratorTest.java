package com.conversationalcommerce.agent.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdkOrchestratorTest {

    @Test
    void stub_when_no_gemini_mentions_api_key_and_vertex_adc() {
        var orchestrator = new AdkOrchestrator(null);
        var response = orchestrator.process("hi", "c1", Map.of());

        assertThat(response.text())
                .contains("GOOGLE_API_KEY")
                .contains("vertex-project")
                .contains("Application Default Credentials");
    }
}
