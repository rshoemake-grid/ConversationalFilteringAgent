package com.conversationalcommerce.agent.ranking;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import com.conversationalcommerce.agent.config.VertexAiRankingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Calls Discovery Engine {@code rankingConfigs/default_ranking_config:rank} with the configured model.
 */
@Service
public class VertexAiRankingService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiRankingService.class);
    private static final String RANK_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final int MAX_RECORDS = 200;
    private static final int MAX_CONTENT_CHARS = 3500;

    private final GcpCredentialsProvider gcpCredentialsProvider;
    private final VertexAiRankingConfig rankingConfig;
    private final ConversationalCommerceConfig commerceConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public VertexAiRankingService(
            GcpCredentialsProvider gcpCredentialsProvider,
            VertexAiRankingConfig rankingConfig,
            ConversationalCommerceConfig commerceConfig,
            ObjectMapper objectMapper) {
        this.gcpCredentialsProvider = gcpCredentialsProvider;
        this.rankingConfig = rankingConfig;
        this.commerceConfig = commerceConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Reorders {@code products} by semantic relevance to {@code query}. Returns the original list on failure or skip.
     */
    public List<AgentResponse.ProductResult> rank(String query, List<AgentResponse.ProductResult> products) {
        if (query == null || query.isBlank() || products == null || products.size() < 2) {
            return products;
        }
        String projectId = commerceConfig.projectId();
        if (projectId == null || projectId.isBlank() || "your-project-id".equals(projectId)) {
            log.debug("Skipping semantic rerank: conversational-commerce.project-id is not set");
            return products;
        }

        List<AgentResponse.ProductResult> batch = products.size() > MAX_RECORDS
                ? products.subList(0, MAX_RECORDS)
                : products;

        try {
            GoogleCredentials creds = gcpCredentialsProvider.getCredentials().createScoped(List.of(RANK_SCOPE));
            creds.refreshIfExpired();
            String token = creds.getAccessToken().getTokenValue();

            String url = "https://discoveryengine.googleapis.com/v1/projects/"
                    + projectId
                    + "/locations/global/rankingConfigs/default_ranking_config:rank";

            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", rankingConfig.model());
            root.put("query", query.trim());

            ArrayNode records = objectMapper.createArrayNode();
            for (int i = 0; i < batch.size(); i++) {
                AgentResponse.ProductResult p = batch.get(i);
                ObjectNode r = objectMapper.createObjectNode();
                r.put("id", String.valueOf(i));
                r.put("title", Objects.toString(p.title(), ""));
                r.put("content", truncate(buildContent(p), MAX_CONTENT_CHARS));
                records.add(r);
            }
            root.set("records", records);

            String body = objectMapper.writeValueAsString(root);
            String quotaHeader = commerceConfig.projectId();
            String qp = gcpCredentialsProvider.getQuotaProject();
            if (qp != null && !qp.isBlank()) {
                quotaHeader = qp;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header(GcpCredentialsProvider.quotaProjectHeaderName(), quotaHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("Vertex AI semantic reranking failed: HTTP {} — {}", response.statusCode(),
                        truncate(response.body(), 500));
                return products;
            }

            JsonNode tree = objectMapper.readTree(response.body());
            JsonNode outRecords = tree.path("records");
            if (!outRecords.isArray() || outRecords.isEmpty()) {
                log.warn("Vertex AI semantic reranking returned no records in response body");
                return products;
            }

            List<IndexedScore> ranked = new ArrayList<>();
            for (JsonNode rec : outRecords) {
                int idx;
                try {
                    idx = Integer.parseInt(rec.path("id").asText(""));
                } catch (NumberFormatException e) {
                    continue;
                }
                double score = rec.path("score").asDouble(Double.NaN);
                if (idx >= 0 && idx < batch.size() && !Double.isNaN(score)) {
                    ranked.add(new IndexedScore(idx, score));
                }
            }
            if (ranked.size() != batch.size()) {
                log.warn("Vertex AI semantic reranking response size mismatch; keeping original order");
                return products;
            }
            ranked.sort(Comparator.comparingDouble(IndexedScore::score).reversed());
            List<AgentResponse.ProductResult> reordered = new ArrayList<>(batch.size());
            for (IndexedScore is : ranked) {
                reordered.add(batch.get(is.index));
            }
            if (products.size() > MAX_RECORDS) {
                reordered.addAll(products.subList(MAX_RECORDS, products.size()));
            }
            log.info("Vertex AI semantic reranking applied (model={}, records={})", rankingConfig.model(), batch.size());
            return reordered;
        } catch (Exception e) {
            log.warn("Vertex AI semantic reranking failed: {}", e.getMessage());
            return products;
        }
    }

    private static String buildContent(AgentResponse.ProductResult p) {
        StringBuilder sb = new StringBuilder();
        if (p.description() != null) sb.append(p.description()).append("\n");
        if (p.brands() != null) sb.append(String.join(" ", p.brands())).append("\n");
        if (p.categories() != null) sb.append(String.join(" ", p.categories())).append("\n");
        if (p.attributes() != null) {
            sb.append(p.attributes().toString());
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? Objects.toString(p.title(), "") : s;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }

    private record IndexedScore(int index, double score) {}
}
