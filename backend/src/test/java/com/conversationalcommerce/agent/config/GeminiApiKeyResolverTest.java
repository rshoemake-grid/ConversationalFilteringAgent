package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiApiKeyResolverTest {

    @Test
    void prefers_app_gemini_api_key() {
        var env = new MockEnvironment()
                .withProperty("app.gemini.api-key", "from-yaml")
                .withProperty("GOOGLE_API_KEY", "from-env");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("from-yaml");
        assertThat(GeminiApiKeyResolver.isPresent(env)).isTrue();
    }

    @Test
    void falls_back_to_GOOGLE_API_KEY() {
        var env = new MockEnvironment().withProperty("GOOGLE_API_KEY", "from-google");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("from-google");
    }

    @Test
    void falls_back_to_env_GOOGLE_API_KEY_block() {
        var env = new MockEnvironment().withProperty("env.GOOGLE_API_KEY", "from-env-block");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("from-env-block");
    }

    @Test
    void returns_null_when_missing() {
        assertThat(GeminiApiKeyResolver.resolve(new MockEnvironment())).isNull();
        assertThat(GeminiApiKeyResolver.isPresent(new MockEnvironment())).isFalse();
    }
}
