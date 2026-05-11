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
    void prefers_camelCase_app_gemini_apiKey() {
        var env = new MockEnvironment()
                .withProperty("app.gemini.apiKey", "from-camel")
                .withProperty("GOOGLE_API_KEY", "from-env");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("from-camel");
    }

    @Test
    void falls_back_to_GEMINI_API_KEY() {
        var env = new MockEnvironment().withProperty("GEMINI_API_KEY", "from-gemini-env");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("from-gemini-env");
    }

    @Test
    void skips_placeholder_and_uses_next_source() {
        var env = new MockEnvironment()
                .withProperty("app.gemini.api-key", "enter api key here")
                .withProperty("GOOGLE_API_KEY", "real-key");
        assertThat(GeminiApiKeyResolver.resolve(env)).isEqualTo("real-key");
    }

    @Test
    void hasUsableApiKey_falseWhenSanitizedEmpty() {
        var env = new MockEnvironment().withProperty("app.gemini.api-key", "#only-comment");
        assertThat(GeminiApiKeyResolver.hasUsableApiKey(env)).isFalse();
    }

    @Test
    void hasUsableApiKey_trueWhenClean() {
        var env = new MockEnvironment().withProperty("GOOGLE_API_KEY", "k");
        assertThat(GeminiApiKeyResolver.hasUsableApiKey(env)).isTrue();
    }

    @Test
    void returns_null_when_missing() {
        assertThat(GeminiApiKeyResolver.resolve(new MockEnvironment())).isNull();
        assertThat(GeminiApiKeyResolver.isPresent(new MockEnvironment())).isFalse();
    }
}
