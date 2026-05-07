package com.conversationalcommerce.agent.gemini;

import com.conversationalcommerce.agent.config.ApiKeySanitizer;
import com.conversationalcommerce.agent.config.GeminiApiKeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists Gemini models that support generateContent. Only active when GOOGLE_API_KEY or app.gemini.api-key is set.
 */
@Service
@ConditionalOnExpression("T(com.conversationalcommerce.agent.config.GeminiApiKeyResolver).isPresent(@environment)")
public class GeminiModelsService {

    private static final String MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final Environment environment;

    @Value("${app.gemini.referer:http://localhost:5173/}")
    private String referer;

    public GeminiModelsService(Environment environment) {
        this.environment = environment;
    }

    private String getRawApiKey() {
        return GeminiApiKeyResolver.resolve(environment);
    }

    /**
     * Returns model names (e.g. "gemini-2.5-flash") that support generateContent.
     */
    public List<String> listModels() {
        String rawKey = getRawApiKey();
        String sanitizedKey = ApiKeySanitizer.sanitize(rawKey);
        if (sanitizedKey == null || sanitizedKey.isBlank()) {
            return List.of();
        }

        var uri = URI.create(MODELS_URL + "?key=" + sanitizedKey);
        var request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Referer", referer)
                .build();

        try {
            var response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new RuntimeException(response.body());
            }
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
            var models = json.get("models");
            if (models == null || !models.isArray()) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (var model : models) {
                var nameNode = model.get("name");
                if (nameNode == null) continue;
                String name = nameNode.asText();
                if (name.startsWith("models/")) {
                    name = name.substring(7);
                }
                var methods = model.get("supportedGenerationMethods");
                if (methods != null && methods.isArray()) {
                    boolean hasGenerateContent = false;
                    for (var m : methods) {
                        if ("generateContent".equals(m.asText())) {
                            hasGenerateContent = true;
                            break;
                        }
                    }
                    if (hasGenerateContent) {
                        names.add(name);
                    }
                } else {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
