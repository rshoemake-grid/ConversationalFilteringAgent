package com.conversationalcommerce.agent.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Resolves Gemini / Google AI API key from Spring {@link Environment} in a single order of precedence.
 * <p>
 * Order: {@code app.gemini.api-key}, {@code app.gemini.apiKey} (relaxed YAML binding),
 * {@code GOOGLE_API_KEY}, {@code GEMINI_API_KEY}, {@code env.GOOGLE_API_KEY} (YAML {@code env:} block).
 */
public final class GeminiApiKeyResolver {

    private static final String[] PROPERTY_KEYS = {
            "app.gemini.api-key",
            "app.gemini.apiKey",
            "GOOGLE_API_KEY",
            "GEMINI_API_KEY",
            "env.GOOGLE_API_KEY",
    };

    private GeminiApiKeyResolver() {}

    /**
     * @return first non-blank raw key, or {@code null}
     */
    public static String resolve(Environment environment) {
        if (environment == null) {
            return null;
        }
        for (String key : PROPERTY_KEYS) {
            String v = environment.getProperty(key);
            if (!StringUtils.hasText(v)) {
                continue;
            }
            v = v.trim();
            if (isDocumentationPlaceholder(v)) {
                continue;
            }
            return v;
        }
        return null;
    }

    /** Example-file placeholders — skip so a later source (e.g. env.GOOGLE_API_KEY) can supply the real key. */
    private static boolean isDocumentationPlaceholder(String v) {
        String t = v.trim();
        return "enter api key here".equalsIgnoreCase(t)
                || "your-api-key-here".equalsIgnoreCase(t)
                || "<your-api-key>".equalsIgnoreCase(t);
    }

    public static boolean isPresent(Environment environment) {
        return StringUtils.hasText(resolve(environment));
    }

    /**
     * True if resolving yields a key that {@link ApiKeySanitizer} keeps as non-blank
     * (excludes values that become empty after stripping comments/newlines).
     */
    public static boolean hasUsableApiKey(Environment environment) {
        String raw = resolve(environment);
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String sanitized = ApiKeySanitizer.sanitize(raw);
        return StringUtils.hasText(sanitized);
    }
}
