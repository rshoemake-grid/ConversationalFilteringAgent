package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * REST-based Product.Get for enriching search results with missing fields.
 * Active when transport=rest.
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') == 'rest'")
public class RetailProductFetcherRest implements ProductFetcher {

    private static final Logger log = LoggerFactory.getLogger(RetailProductFetcherRest.class);
    private static final String BASE_URL = "https://retail.googleapis.com/v2";

    private final GcpCredentialsProvider credentialsProvider;
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder().build();

    public RetailProductFetcherRest(GcpCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    @Override
    public Optional<AgentResponse.ProductResult> getProduct(String productName) {
        if (productName == null || productName.isBlank() || !productName.contains("/products/")) {
            return Optional.empty();
        }
        try {
            String url = BASE_URL + "/" + productName;
            var credentials = credentialsProvider.getCredentials();
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();

            var requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token);
            String quotaProject = credentialsProvider.getQuotaProject();
            if (quotaProject != null && !quotaProject.isEmpty()) {
                requestBuilder.header(GcpCredentialsProvider.quotaProjectHeaderName(), quotaProject);
            }
            var request = requestBuilder.GET().build();

            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("Product.Get failed {} for {}: {}", response.statusCode(), productName, response.body());
                return Optional.empty();
            }
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> product = mapper.readValue(response.body(), Map.class);
            AgentResponse.ProductResult result = ProductResponseParser.fromProductMap(product, null, true);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.debug("Product.Get failed for {}: {}", productName, e.getMessage());
            return Optional.empty();
        }
    }
}
