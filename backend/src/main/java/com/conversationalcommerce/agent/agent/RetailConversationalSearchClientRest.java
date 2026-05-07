package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST-based implementation using HTTP/1.1. Use when gRPC fails (e.g. "Failed ALPN negotiation" behind VPN/proxy).
 * Set conversational-commerce.transport=rest to enable.
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') == 'rest'")
public class RetailConversationalSearchClientRest implements ConversationalCommerceClient {

    private static final Logger log = LoggerFactory.getLogger(RetailConversationalSearchClientRest.class);
    private static final String BASE_URL = "https://retail.googleapis.com/v2";

    /** HTTP status codes worth retrying (transient failures). */
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 502, 503, 504);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 500;

    private final ConversationalCommerceConfig config;
    private final GcpCredentialsProvider credentialsProvider;
    private final BrandDisplayResolver brandDisplayResolver;
    private final HttpClient httpClient;

    public RetailConversationalSearchClientRest(ConversationalCommerceConfig config,
                                                GcpCredentialsProvider credentialsProvider,
                                                BrandDisplayResolver brandDisplayResolver) {
        this.config = config;
        this.credentialsProvider = credentialsProvider;
        this.brandDisplayResolver = brandDisplayResolver;
        this.httpClient = HttpClient.newBuilder().build();
        log.info("Using REST transport for GCP Conversational Commerce (bypasses gRPC/ALPN)");
    }

    @Override
    public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
        String url = BASE_URL + "/" + request.placement() + ":conversationalSearch";
        String body = buildRequestBody(request);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest httpRequest = buildHttpRequest(url, body);
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() == 200) {
                    var result = parseResponse(response.body());
                    if (log.isDebugEnabled()) {
                        log.debug("Conversational search: requestConvId={} responseConvId={} query={}",
                                request.conversationId(), result.conversationId(), request.query());
                    }
                    return result;
                }

                if (RETRYABLE_STATUS_CODES.contains(response.statusCode()) && attempt < MAX_ATTEMPTS) {
                    long backoffMs = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("GCP REST API returned {} (attempt {}/{}), retrying in {}ms: {}",
                            response.statusCode(), attempt, MAX_ATTEMPTS, backoffMs, response.body());
                    Thread.sleep(backoffMs);
                } else {
                    throw new RuntimeException("REST API error: " + response.statusCode() + " " + response.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("REST conversational search interrupted", e);
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_ATTEMPTS && isRetryableException(e)) {
                    long backoffMs = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("REST conversational search failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_ATTEMPTS, backoffMs, e.getMessage());
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("REST conversational search interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        throw new RuntimeException("REST conversational search failed: " + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    private HttpRequest buildHttpRequest(String url, String body) {
        try {
            GoogleCredentials credentials = credentialsProvider.getCredentials();
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        String quotaProject = credentialsProvider.getQuotaProject();
        if (quotaProject != null && !quotaProject.isEmpty()) {
            requestBuilder.header(GcpCredentialsProvider.quotaProjectHeaderName(), quotaProject);
        }
        return requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build REST request: " + e.getMessage(), e);
        }
    }

    /** True for network/IO errors that may be transient. */
    private static boolean isRetryableException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return e instanceof IOException
                || msg.contains("Connection reset")
                || msg.contains("connection refused")
                || msg.contains("timeout")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("429");
    }

    private String buildRequestBody(ConversationalCommerceRequest request) {
        String mode = config.conversationalFilteringMode();
        String filteringSpec = "\"conversationalFilteringMode\": \"" + escapeJson(mode) + "\"";
        return """
                {
                  "branch": "%s",
                  "query": "%s",
                  "conversationId": "%s",
                  "visitorId": "%s",
                  "conversationalFilteringSpec": { %s }
                }
                """.formatted(
                escapeJson(request.branch()),
                escapeJson(request.query()),
                escapeJson(request.conversationId() != null ? request.conversationId() : ""),
                escapeJson(request.visitorId()),
                filteringSpec
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Removes duplicated sentences (e.g. when streaming sends same content twice). */
    private static String deduplicateText(String text) {
        if (text == null || text.length() < 2) return text;
        int mid = text.length() / 2;
        String first = text.substring(0, mid).trim();
        String second = text.substring(mid).trim();
        if (first.equals(second)) return first;
        return text;
    }

    @SuppressWarnings("unchecked")
    private ConversationalCommerceResult parseResponse(String json) {
        List<String> textParts = new ArrayList<>();
        final List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers = new ArrayList<>();
        final String[] conversationId = {""};
        final String[] refinedQuery = {""};
        final String[] queryType = {""};
        final String[] followupQuestion = {""};

        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object parsed = mapper.readValue(json, Object.class);
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        extractFromMap((Map<String, Object>) m, textParts, suggestedAnswers,
                                s -> conversationId[0] = s, s -> refinedQuery[0] = s, s -> queryType[0] = s, s -> followupQuestion[0] = s);
                    }
                }
            } else if (parsed instanceof Map<?, ?> m) {
                extractFromMap((Map<String, Object>) m, textParts, suggestedAnswers,
                        s -> conversationId[0] = s, s -> refinedQuery[0] = s, s -> queryType[0] = s, s -> followupQuestion[0] = s);
            }
        } catch (Exception e) {
            // Streaming API may return NDJSON (newline-delimited JSON chunks)
            try {
                for (String line : json.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(trimmed, Object.class);
                    if (parsed instanceof Map<?, ?> m) {
                        extractFromMap((Map<String, Object>) m, textParts, suggestedAnswers,
                                s -> conversationId[0] = s, s -> refinedQuery[0] = s, s -> queryType[0] = s, s -> followupQuestion[0] = s);
                    }
                }
            } catch (Exception e2) {
                log.warn("Failed to parse REST response (tried single JSON and NDJSON): {}", e.getMessage());
            }
        }

        String text = deduplicateText(String.join(" ", textParts));
        String source = "agent";
        if ("RETAIL_IRRELEVANT".equals(queryType[0])) {
            text = "I didn't understand your response.";
            source = "app";
        } else if (text.isEmpty() && !followupQuestion[0].isEmpty()) {
            text = followupQuestion[0];
        } else if (text.isEmpty()) {
            if (refinedQuery[0] != null && !refinedQuery[0].isEmpty()) {
                text = "Searching for: " + refinedQuery[0];
            } else if ("SIMPLE_PRODUCT_SEARCH".equals(queryType[0])) {
                text = "No products found.";
            } else {
                text = "I didn't understand your response.";
            }
            source = "app";
        }
        if (!followupQuestion[0].isEmpty() && !text.equals(followupQuestion[0])) {
            text = text + "\n\n" + followupQuestion[0];
        }

        if (!suggestedAnswers.isEmpty() && log.isInfoEnabled()) {
            log.info("Parsed {} suggested answers: {}", suggestedAnswers.size(),
                    suggestedAnswers.stream().map(ConversationalCommerceClient.SuggestedAnswer::displayText).toList());
        }
        return new ConversationalCommerceResult(text, conversationId[0], refinedQuery[0], queryType[0], source, json, suggestedAnswers);
    }

    @SuppressWarnings("unchecked")
    private void extractFromMap(Map<String, Object> map, List<String> textParts, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
                               java.util.function.Consumer<String> setConversationId,
                               java.util.function.Consumer<String> setRefinedQuery,
                               java.util.function.Consumer<String> setQueryType,
                               java.util.function.Consumer<String> setFollowupQuestion) {
        if (map == null) return;
        // Check top-level and nested conversationalSearchResult (GCP nests it in SearchResponse)
        Map<String, Object> toScan = map;
        if (map.containsKey("conversationalSearchResult") && map.get("conversationalSearchResult") instanceof Map<?, ?> csr) {
            toScan = (Map<String, Object>) csr;
        }
        String refinedQueryForLookup = extractRefinedQueryFromMap(map, toScan);
        // suggestedAnswers: from conversationalSearchResult or conversationalFilteringResult
        extractSuggestedAnswers(toScan, suggestedAnswers, refinedQueryForLookup);
        Object cfrObj = map.get("conversationalFilteringResult");
        if (cfrObj == null) cfrObj = map.get("conversational_filtering_result");
        if (cfrObj instanceof Map<?, ?> cfr) {
            Map<String, Object> cfrMap = (Map<String, Object>) cfr;
            extractSuggestedAnswers(cfrMap, suggestedAnswers, refinedQueryForLookup);
            // Nested format: conversationalFilteringResult.followupQuestion.suggestedAnswers
            Object fq = cfrMap.get("followupQuestion");
            if (fq == null) fq = cfrMap.get("followup_question");
            if (fq instanceof Map<?, ?> fqMap) {
                extractSuggestedAnswers((Map<String, Object>) fqMap, suggestedAnswers, refinedQueryForLookup);
            }
        }
        Object convId = toScan.get("conversationId");
        if (convId == null) convId = toScan.get("conversation_id");
        if (convId != null) {
            String s = String.valueOf(convId).trim();
            if (!s.isEmpty()) setConversationId.accept(s);
        }
        // refinedQuery: from refinedSearch (streaming) or conversationalSearchResult
        if (map.containsKey("refinedSearch") && map.get("refinedSearch") instanceof List<?> rs
                && !rs.isEmpty() && rs.get(0) instanceof Map<?, ?> rq) {
            Object q = ((Map<?, ?>) rq).get("query");
            if (q != null) setRefinedQuery.accept(String.valueOf(q));
        } else {
            Object rq = toScan.get("refinedQuery");
            if (rq != null) {
                String s = String.valueOf(rq).trim();
                if (!s.isEmpty()) setRefinedQuery.accept(s);
            }
        }
        if (map.containsKey("userQueryTypes") && map.get("userQueryTypes") instanceof List<?> uqt
                && !uqt.isEmpty()) {
            setQueryType.accept(String.valueOf(uqt.get(0)));
        } else {
            Object qt = toScan.get("queryType");
            if (qt == null) qt = toScan.get("queryTypes");
            if (qt instanceof List<?> qtl && !qtl.isEmpty()) {
                setQueryType.accept(String.valueOf(qtl.get(0)));
            } else if (qt != null) {
                String s = String.valueOf(qt).trim();
                if (!s.isEmpty()) setQueryType.accept(s);
            }
        }
        Object textObj = map.get("conversationalTextResponse");
        if (textObj == null) textObj = toScan.get("textResult");
        if (textObj != null) {
            String part = String.valueOf(textObj).trim();
            if (!part.isEmpty()) {
                if (!textParts.isEmpty()) {
                    textParts.set(textParts.size() - 1, part);
                } else {
                    textParts.add(part);
                }
            }
        }
        String fq = extractFollowupQuestion(map);
        if (fq.isEmpty() && toScan != map) fq = extractFollowupQuestion(toScan);
        if (!fq.isEmpty()) setFollowupQuestion.accept(fq);
    }

    @SuppressWarnings("unchecked")
    private static String extractRefinedQueryFromMap(Map<String, Object> map, Map<String, Object> toScan) {
        if (map != null && map.containsKey("refinedSearch") && map.get("refinedSearch") instanceof List<?> rs
                && !rs.isEmpty() && rs.get(0) instanceof Map<?, ?> rq) {
            Object q = ((Map<?, ?>) rq).get("query");
            if (q != null) {
                String s = String.valueOf(q).trim();
                if (!s.isEmpty()) return s;
            }
        }
        if (toScan != null) {
            Object rq = toScan.get("refinedQuery");
            if (rq == null) rq = toScan.get("refined_query");
            if (rq != null) {
                String s = String.valueOf(rq).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void extractSuggestedAnswers(Map<String, Object> map, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers, String searchQueryHint) {
        if (map == null) return;
        Object saObj = map.get("suggestedAnswers");
        if (saObj == null) saObj = map.get("suggested_answers");
        if (!(saObj instanceof List<?> saList)) return;
        for (Object item : saList) {
            String value = null;
            String attributeName = null;
            if (item instanceof String) {
                value = ((String) item).trim();
            } else if (item instanceof Map<?, ?> m) {
                Object pav = ((Map<?, ?>) m).get("productAttributeValue");
                if (pav == null) pav = ((Map<?, ?>) m).get("product_attribute_value");
                if (pav instanceof Map<?, ?> pavMap) {
                    Object v = ((Map<?, ?>) pavMap).get("value");
                    if (v != null) value = String.valueOf(v).trim();
                    Object n = ((Map<?, ?>) pavMap).get("name");
                    if (n != null) attributeName = String.valueOf(n).trim();
                }
                if (value == null || value.isEmpty()) {
                    Object t = ((Map<?, ?>) m).get("text");
                    if (t == null) t = ((Map<?, ?>) m).get("answer");
                    if (t != null) value = String.valueOf(t).trim();
                }
            } else if (item != null) {
                value = String.valueOf(item).trim();
            }
            if (value != null && !value.isEmpty()) {
                String displayText = resolveDisplayText(attributeName, value, searchQueryHint);
                suggestedAnswers.add(new ConversationalCommerceClient.SuggestedAnswer(displayText, value));
            }
        }
    }

    /** Resolve display text from attribute name and raw value. Delegates to BrandDisplayResolver. */
    private String resolveDisplayText(String attributeName, String value, String searchQueryHint) {
        return brandDisplayResolver.resolveDisplayText(attributeName, value, searchQueryHint);
    }

    @SuppressWarnings("unchecked")
    private String extractFollowupQuestion(Map<String, Object> map) {
        if (map == null) return "";
        // Direct string (ConversationalSearchResult format)
        Object fqObj = map.get("followupQuestion");
        if (fqObj != null) {
            if (fqObj instanceof String s && !s.trim().isEmpty()) return s.trim();
            if (fqObj instanceof Map<?, ?> fqMap) {
                Object q = ((Map<?, ?>) fqMap).get("followupQuestion");
                if (q != null) {
                    String s = String.valueOf(q).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        if (map.containsKey("conversationalFilteringResult") && map.get("conversationalFilteringResult") instanceof Map<?, ?> cfr) {
            Object cfrFq = ((Map<?, ?>) cfr).get("followupQuestion");
            if (cfrFq == null) cfrFq = ((Map<?, ?>) cfr).get("followup_question");
            if (cfrFq instanceof String s && !s.trim().isEmpty()) return s.trim();
            if (cfrFq instanceof Map<?, ?> fq) {
                Object q = ((Map<?, ?>) fq).get("followupQuestion");
                if (q == null) q = ((Map<?, ?>) fq).get("followup_question");
                if (q != null) {
                    String s = String.valueOf(q).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return "";
    }
}
