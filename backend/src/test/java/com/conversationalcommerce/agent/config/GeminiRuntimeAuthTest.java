package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiRuntimeAuthTest {

    @Test
    void canUseGemini_trueWhenApiKeyResolvable() {
        var env = new MockEnvironment().withProperty("GOOGLE_API_KEY", "some-key");
        assertThat(GeminiRuntimeAuth.canUseGemini(env)).isTrue();
    }

    @Test
    void canUseGemini_trueWhenVertexProjectSet() {
        var env = new MockEnvironment().withProperty("GCP_PROJECT_ID", "my-gcp-project-123");
        assertThat(GeminiRuntimeAuth.canUseGemini(env)).isTrue();
    }

    @Test
    void canUseGemini_falseWhenNothingConfigured() {
        assertThat(GeminiRuntimeAuth.canUseGemini(new MockEnvironment())).isFalse();
    }

    @Test
    void canUseGemini_falseForPlaceholderProject() {
        var env = new MockEnvironment().withProperty("GCP_PROJECT_ID", "your-project-id");
        assertThat(GeminiRuntimeAuth.canUseGemini(env)).isFalse();
    }
}
