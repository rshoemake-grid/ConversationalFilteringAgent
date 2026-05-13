package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ClarifyingFollowUpPolicy;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last conversational-filtering / clarifying question per chat {@code sessionId}
 * so storage-chip recovery can re-ask with the same wording when the client omits
 * {@code previousAssistantText}.
 */
@Component
public class LastFilteringQuestionStore {

    private final ConcurrentHashMap<String, String> bySession = new ConcurrentHashMap<>();

    /**
     * Updates stored question from the assistant response (after each orchestrator turn).
     */
    public void rememberFromResponse(String sessionId, AgentResponse response) {
        if (sessionId == null || sessionId.isBlank() || response == null) {
            return;
        }
        String key = sessionId.trim();
        String clarifying = response.clarifyingQuestion();
        if (clarifying != null && !clarifying.isBlank()) {
            bySession.put(key, clarifying.trim());
            return;
        }
        List<ConversationalCommerceClient.SuggestedAnswer> sas = response.suggestedAnswers();
        if (sas != null && !sas.isEmpty() && hasStorageSuggestion(sas)) {
            String text = response.text();
            if (text != null && text.contains("?")) {
                String line = extractStockRelatedQuestionLine(text);
                if (line != null && !line.isBlank()) {
                    bySession.put(key, line.trim());
                }
            }
        }
    }

    public Optional<String> getLastQuestion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String v = bySession.get(sessionId.trim());
        return v != null && !v.isBlank() ? Optional.of(v) : Optional.empty();
    }

    private static boolean hasStorageSuggestion(List<ConversationalCommerceClient.SuggestedAnswer> sas) {
        for (var sa : sas) {
            if (ClarifyingFollowUpPolicy.isStorageSuggestionValue(sa.value())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prefer a line that clearly asks about stock/storage; else first line containing {@code '?'}.
     */
    private static String extractStockRelatedQuestionLine(String text) {
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.contains("?") && ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice(line)) {
                return line;
            }
        }
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.contains("?")) {
                return line;
            }
        }
        return null;
    }
}
