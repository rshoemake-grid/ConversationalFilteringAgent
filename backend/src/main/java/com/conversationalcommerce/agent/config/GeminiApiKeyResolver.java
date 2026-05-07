package com.conversationalcommerce.agent.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Resolves Gemini / Google AI API key from Spring {@link Environment} in a single order of precedence.
 * Sources: {@code app.gemini.api-key}, {@code GOOGLE_API_KEY}, {@code env.GOOGLE_API_KEY} (YAML block).
 */
public final class GeminiApiKeyResolver {

    private GeminiApiKeyResolver() {}

    /**
     * @return first non-blank raw key, or {@code null}
     */
    public static String resolve(Environment environment) {
        if (environment == null) {
            return null;
        }
        String v = environment.getProperty("app.gemini.api-key");
        if (StringUtils.hasText(v)) {
            return v.trim();
        }
        v = environment.getProperty("GOOGLE_API_KEY");
        if (StringUtils.hasText(v)) {
            return v.trim();
        }
        v = environment.getProperty("env.GOOGLE_API_KEY");
        if (StringUtils.hasText(v)) {
            return v.trim();
        }
        return null;
    }

    public static boolean isPresent(Environment environment) {
        return StringUtils.hasText(resolve(environment));
    }
}
