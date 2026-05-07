package com.conversationalcommerce.agent.gemini;

import com.conversationalcommerce.agent.config.ApiKeySanitizer;
import com.conversationalcommerce.agent.config.GeminiApiKeyResolver;
import com.conversationalcommerce.agent.config.GeminiClientFactory;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Uses Gemini to generate contextual clarifying questions when many products match a search.
 * Only active when GOOGLE_API_KEY or app.gemini.api-key is set.
 */
@Service
@ConditionalOnExpression("T(com.conversationalcommerce.agent.config.GeminiApiKeyResolver).isPresent(@environment)")
public class ClarifyingQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClarifyingQuestionGenerator.class);

    private final Environment environment;
    private final GeminiClientFactory clientFactory;

    @Value("${app.gemini.model:gemini-flash-latest}")
    private String model;

    public ClarifyingQuestionGenerator(Environment environment, GeminiClientFactory clientFactory) {
        this.environment = environment;
        this.clientFactory = clientFactory;
    }

    private String getRawApiKey() {
        return GeminiApiKeyResolver.resolve(environment);
    }

    /**
     * Generates a brief, conversational message asking the user to narrow down their search.
     * Returns null on failure (caller should use fallback).
     */
    public String generate(String refinedQuery, int productCount) {
        String rawKey = getRawApiKey();
        String sanitizedKey = ApiKeySanitizer.sanitize(rawKey);
        if (sanitizedKey == null || sanitizedKey.isBlank()) {
            return null;
        }

        String prompt = """
            You are a helpful shopping assistant. We found %d products matching "%s".
            Write ONE short, friendly sentence asking the user to narrow down their search.
            Tailor your question to the product category (e.g. for food: type, form, size; for clothing: style, color).
            No preamble. Just the question. Max 2 sentences.
            """.formatted(productCount, refinedQuery);

        try (Client client = clientFactory.buildClient(sanitizedKey)) {
            var response = client.models.generateContent(
                    model,
                    prompt,
                    GenerateContentConfig.builder().build());
            String text = response != null ? response.text() : null;
            return (text != null && !text.isBlank()) ? text.trim() : null;
        } catch (Exception e) {
            log.warn("Clarifying question generation failed: {}", e.getMessage());
            return null;
        }
    }
}
