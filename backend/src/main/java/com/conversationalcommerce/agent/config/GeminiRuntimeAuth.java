package com.conversationalcommerce.agent.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Whether Gemini-backed features (ADK, clarifying questions, /api/models) can run:
 * a usable API key, or a GCP project for Vertex AI with Application Default Credentials.
 */
public final class GeminiRuntimeAuth {

    private GeminiRuntimeAuth() {}

    public static boolean canUseGemini(Environment environment) {
        if (environment == null) {
            return false;
        }
        if (GeminiApiKeyResolver.hasUsableApiKey(environment)) {
            return true;
        }
        return hasVertexProjectForAdc(environment);
    }

    /** True when {@code app.gemini.vertex-project} or {@code GCP_PROJECT_ID} is set to a non-placeholder project. */
    public static boolean hasVertexProjectForAdc(Environment env) {
        String p = env.getProperty("app.gemini.vertex-project");
        if (!StringUtils.hasText(p)) {
            p = env.getProperty("GCP_PROJECT_ID");
        }
        if (!StringUtils.hasText(p)) {
            return false;
        }
        p = p.trim();
        if ("your-project-id".equalsIgnoreCase(p)) {
            return false;
        }
        return true;
    }
}
