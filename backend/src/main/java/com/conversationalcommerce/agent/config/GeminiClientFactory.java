package com.conversationalcommerce.agent.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Builds GenAI {@link Client} either with a Google AI Studio API key or, when no key is configured,
 * with Vertex AI using Application Default Credentials (and optional service account path via
 * {@link GcpCredentialsProvider}).
 */
@Component
public class GeminiClientFactory {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    @Value("${app.gemini.referer:}")
    private String referer;

    private final GcpCredentialsProvider gcpCredentialsProvider;

    public GeminiClientFactory(GcpCredentialsProvider gcpCredentialsProvider) {
        this.gcpCredentialsProvider = gcpCredentialsProvider;
    }

    /**
     * Prefers API key when a usable key is present; otherwise uses Vertex AI with ADC when a project id is configured.
     */
    public Client buildClientForGemini(Environment environment) throws Exception {
        String raw = GeminiApiKeyResolver.resolve(environment);
        String sanitized = ApiKeySanitizer.sanitize(raw);
        if (StringUtils.hasText(sanitized)) {
            return buildClientWithApiKey(sanitized);
        }
        return buildClientWithVertexAdc(environment);
    }

    /**
     * Builds a Client with the given API key. If {@code app.gemini.referer} is set, adds Referer header
     * (required when the API key has website restrictions in Google Cloud Console).
     */
    public Client buildClientWithApiKey(String apiKey) {
        var clientBuilder = Client.builder().apiKey(apiKey);
        if (referer != null && !referer.isBlank()) {
            clientBuilder.httpOptions(
                    HttpOptions.builder().headers(Map.of("Referer", referer)).build());
        }
        return clientBuilder.build();
    }

    private Client buildClientWithVertexAdc(Environment environment) throws Exception {
        if (!GeminiRuntimeAuth.hasVertexProjectForAdc(environment)) {
            throw new IllegalStateException(
                    "No Gemini API key and no Vertex/GCP project for ADC. Set app.gemini.api-key, GOOGLE_API_KEY, "
                            + "or app.gemini.vertex-project / GCP_PROJECT_ID and ensure Application Default Credentials "
                            + "are available (e.g. gcloud auth application-default login).");
        }
        String project = environment.getProperty("app.gemini.vertex-project");
        if (!StringUtils.hasText(project)) {
            project = environment.getProperty("GCP_PROJECT_ID");
        }
        String location = environment.getProperty("app.gemini.vertex-location", "us-central1");
        GoogleCredentials creds = gcpCredentialsProvider.getCredentials()
                .createScoped(Collections.singletonList(CLOUD_PLATFORM_SCOPE));
        return Client.builder()
                .vertexAI(true)
                .project(project.trim())
                .location(location.trim())
                .credentials(creds)
                .build();
    }
}
