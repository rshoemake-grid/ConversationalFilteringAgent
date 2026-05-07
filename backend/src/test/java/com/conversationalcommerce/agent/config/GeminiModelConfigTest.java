package com.conversationalcommerce.agent.config;

import com.google.adk.agents.LlmAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies app.gemini.model is configurable via application.yml or GEMINI_MODEL env var.
 */
@SpringBootTest(classes = {GeneralQuestionAgentConfig.class, GeminiClientFactory.class})
@TestPropertySource(properties = {"app.gemini.model=gemini-flash-latest", "app.gemini.api-key=\\#no-key"})
class GeminiModelConfigTest {

    @Autowired
    LlmAgent generalQuestionAgent;

    @Test
    void loadsAgentWithConfiguredModel() {
        assertThat(generalQuestionAgent).isNotNull();
        String modelStr = generalQuestionAgent.model()
                .flatMap(m -> m.modelName())
                .orElse("");
        assertThat(modelStr).isEqualTo("gemini-flash-latest");
    }
}
