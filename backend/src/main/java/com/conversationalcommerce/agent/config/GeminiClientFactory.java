package com.conversationalcommerce.agent.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds GenAI Client with optional Referer header for API keys that have website restrictions.
 */
@Component
public class GeminiClientFactory {

    @Value("${app.gemini.referer:}")
    private String referer;

    /**
     * Builds a Client with the given API key. If app.gemini.referer is set, adds Referer header
     * (required when the API key has website restrictions in Google Cloud Console).
     */
    public Client buildClient(String apiKey) {
        var clientBuilder = Client.builder().apiKey(apiKey);
        if (referer != null && !referer.isBlank()) {
            clientBuilder.httpOptions(
                    HttpOptions.builder().headers(Map.of("Referer", referer)).build());
        }
        return clientBuilder.build();
    }
}
