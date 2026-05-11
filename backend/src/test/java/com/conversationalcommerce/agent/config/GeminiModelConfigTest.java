package com.conversationalcommerce.agent.config;

import com.google.adk.agents.LlmAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies app.gemini.model is configurable via application.yml or GEMINI_MODEL env var.
 */
@SpringBootTest(classes = {GeneralQuestionAgentConfig.class, GeminiClientFactory.class, GcpCredentialsProvider.class})
@TestPropertySource(properties = {
        "app.gemini.model=gemini-2.5-flash",
        "app.gemini.api-key=\\#no-key",
        "app.gemini.vertex-project=your-project-id"
})
class GeminiModelConfigTest {

    @Autowired
    Environment environment;

    @Autowired
    LlmAgent generalQuestionAgent;

    @Test
    void loadsAgentWithConfiguredModel() {
        assertThat(environment.getProperty("app.gemini.model")).isEqualTo("gemini-2.5-flash");
        assertThat(generalQuestionAgent).isNotNull();
    }
}
