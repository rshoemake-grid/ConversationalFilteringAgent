package com.conversationalcommerce.agent.gemini;

import com.conversationalcommerce.agent.config.GeminiClientFactory;
import com.google.genai.Client;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists Gemini models. Uses Google AI Studio API key when set; otherwise Vertex AI with Application Default Credentials.
 */
@Service
@ConditionalOnExpression("T(com.conversationalcommerce.agent.config.GeminiRuntimeAuth).canUseGemini(@environment)")
public class GeminiModelsService {

    private final Environment environment;
    private final GeminiClientFactory clientFactory;

    public GeminiModelsService(Environment environment, GeminiClientFactory clientFactory) {
        this.environment = environment;
        this.clientFactory = clientFactory;
    }

    /**
     * Returns model names (e.g. "gemini-2.5-flash") suitable for generateContent.
     */
    public List<String> listModels() {
        try (Client client = clientFactory.buildClientForGemini(environment)) {
            var pager = client.models.list(ListModelsConfig.builder().build());
            List<String> names = new ArrayList<>();
            for (Model m : pager) {
                m.name().map(GeminiModelsService::shortModelName).ifPresent(names::add);
            }
            return names;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static String shortModelName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        if (fullName.startsWith("models/")) {
            return fullName.substring("models/".length());
        }
        int idx = fullName.lastIndexOf('/');
        return idx >= 0 ? fullName.substring(idx + 1) : fullName;
    }
}
