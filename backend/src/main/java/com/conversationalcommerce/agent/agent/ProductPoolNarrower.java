package com.conversationalcommerce.agent.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Narrows an in-memory product pool using the user's message, optional refined query,
 * and conversational suggested answers (facet-style hints).
 */
@Component
public class ProductPoolNarrower {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "as",
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does",
            "did", "will", "would", "could", "should", "may", "might", "must", "shall", "can",
            "with", "from", "by", "about", "into", "through", "any", "i", "we", "you", "it");

    /**
     * Returns a narrowed list. When nothing matches tokens from message/refined query/suggestions,
     * returns an empty list so the UI can show no matches within the pool.
     */
    public List<AgentResponse.ProductResult> narrow(
            List<AgentResponse.ProductResult> pool,
            String userMessage,
            String refinedQuery,
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers) {

        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        String blob = joinSearchBlob(userMessage, refinedQuery, suggestedAnswers);
        if (blob.isBlank()) {
            return List.copyOf(pool);
        }

        List<String> tokens = tokenize(blob);
        if (tokens.isEmpty()) {
            return List.copyOf(pool);
        }

        record Scored(AgentResponse.ProductResult p, int score) {}

        List<Scored> scored = new ArrayList<>();
        for (AgentResponse.ProductResult pr : pool) {
            String hay = haystack(pr).toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : tokens) {
                if (t.length() >= 2 && hay.contains(t)) {
                    score++;
                }
            }
            // Strong match: user/suggestion value appears as substring (e.g. brand codes)
            if (suggestedAnswers != null) {
                for (var sa : suggestedAnswers) {
                    String v = sa.value();
                    if (v != null && v.length() >= 2 && hay.contains(v.toLowerCase(Locale.ROOT))) {
                        score += 2;
                    }
                    String d = sa.displayText();
                    if (d != null && d.length() >= 2 && hay.contains(d.toLowerCase(Locale.ROOT))) {
                        score += 2;
                    }
                }
            }
            scored.add(new Scored(pr, score));
        }

        boolean anyHit = scored.stream().anyMatch(s -> s.score() > 0);
        if (!anyHit) {
            return List.of();
        }
        return scored.stream()
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .map(Scored::p)
                .toList();
    }

    private static String joinSearchBlob(
            String userMessage,
            String refinedQuery,
            List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers) {
        StringBuilder sb = new StringBuilder();
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append(userMessage.trim()).append(' ');
        }
        if (refinedQuery != null && !refinedQuery.isBlank()) {
            sb.append(refinedQuery.trim()).append(' ');
        }
        if (suggestedAnswers != null) {
            for (var sa : suggestedAnswers) {
                if (sa.displayText() != null && !sa.displayText().isBlank()) {
                    sb.append(sa.displayText().trim()).append(' ');
                }
                if (sa.value() != null && !sa.value().isBlank()) {
                    sb.append(sa.value().trim()).append(' ');
                }
            }
        }
        return sb.toString().trim();
    }

    private static List<String> tokenize(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.length() >= 2 && !STOP_WORDS.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private static String haystack(AgentResponse.ProductResult p) {
        StringBuilder sb = new StringBuilder();
        append(sb, p.title());
        append(sb, p.description());
        append(sb, p.productId());
        append(sb, p.gtin());
        if (p.brands() != null) {
            for (String b : p.brands()) append(sb, b);
        }
        if (p.categories() != null) {
            for (String c : p.categories()) append(sb, c);
        }
        if (p.attributes() != null) {
            for (var e : p.attributes().entrySet()) {
                append(sb, e.getKey());
                if (e.getValue() != null) {
                    append(sb, e.getValue().toString());
                }
            }
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String s) {
        if (s != null && !s.isBlank()) {
            sb.append(s).append(' ');
        }
    }
}
