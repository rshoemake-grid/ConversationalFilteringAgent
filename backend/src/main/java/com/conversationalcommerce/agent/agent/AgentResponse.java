package com.conversationalcommerce.agent.agent;

import java.util.List;
import java.util.Map;

/**
 * Response from a conversational agent.
 * @param source "agent" = Conversational Commerce or ADK agent, "app" = app-generated fallback
 */
public record AgentResponse(
        String text,
        String conversationId,
        String refinedQuery,
        List<ProductResult> products,
        String queryType,
        String source,
        String rawResponse,
        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
        /** Estimated total products (GCP); -1 if unknown. When productTotalSizeIsApproximate, value is pages×pageSize. */
        long productTotalSize,
        /** True when productTotalSize is approximated (pages×pageSize) because raw search didn't provide it. */
        boolean productTotalSizeIsApproximate,
        /** Token to fetch next page; null if no more. */
        String productNextPageToken,
        /** Filter used for search (for load-more to reuse). */
        String productFilter,
        /** Clarifying question to show after products (e.g. "Would you like 12oz or 24oz?"). */
        String clarifyingQuestion
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private String conversationId;
        private String refinedQuery;
        private List<ProductResult> products;
        private String queryType;
        private String source;
        private String rawResponse;
        private List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers;
        private long productTotalSize = -1;
        private boolean productTotalSizeIsApproximate = false;
        private String productNextPageToken;
        private String productFilter;
        private String clarifyingQuestion;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder refinedQuery(String refinedQuery) {
            this.refinedQuery = refinedQuery;
            return this;
        }

        public Builder products(List<ProductResult> products) {
            this.products = products;
            return this;
        }

        public Builder queryType(String queryType) {
            this.queryType = queryType;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder rawResponse(String rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public Builder suggestedAnswers(List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers) {
            this.suggestedAnswers = suggestedAnswers;
            return this;
        }

        public Builder productTotalSize(long v) {
            this.productTotalSize = v;
            return this;
        }

        public Builder productTotalSizeIsApproximate(boolean v) {
            this.productTotalSizeIsApproximate = v;
            return this;
        }

        public Builder productNextPageToken(String v) {
            this.productNextPageToken = v;
            return this;
        }

        public Builder productFilter(String v) {
            this.productFilter = v;
            return this;
        }

        public Builder clarifyingQuestion(String v) {
            this.clarifyingQuestion = v;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(text, conversationId, refinedQuery, products, queryType, source != null ? source : "agent", rawResponse, suggestedAnswers, productTotalSize, productTotalSizeIsApproximate, productNextPageToken, productFilter, clarifyingQuestion);
        }
    }

    /** Product from search. id=resource name; productId=short id; gtin=UPC/GTIN. detailsFetched=true when filled via Product.Get. */
    public record ProductResult(
            String id,
            String title,
            String description,
            String price,
            String imageUri,
            String gtin,
            String productId,
            List<String> categories,
            List<String> brands,
            String uri,
            String availability,
            List<String> sizes,
            List<String> materials,
            Map<String, Object> attributes,
            boolean detailsFetched
    ) {
        public static ProductResult of(String id, String title, String description, String price, String imageUri) {
            return new ProductResult(id, title, description, price, imageUri, null, null, null, null, null, null, null, null, null, false);
        }
    }
}
