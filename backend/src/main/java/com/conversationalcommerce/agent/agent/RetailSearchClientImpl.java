package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.google.cloud.retail.v2beta.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real implementation using GCP Retail SearchServiceClient (gRPC).
 * Only active when transport != rest; use RetailSearchClientRest for REST.
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') != 'rest'")
public class RetailSearchClientImpl implements RetailSearchClient {

    private final SearchServiceClient client;
    private final ConversationalCommerceConfig config;

    public RetailSearchClientImpl(ConversationalCommerceConfig config) throws Exception {
        this.client = SearchServiceClient.create();
        this.config = config;
    }

    @Override
    public SearchResult searchWithPagination(String placement, String branch, String query, String visitorId,
                                             String filter, String pageToken, Integer pageSizeOverride) {
        int pageSize = pageSizeOverride != null && pageSizeOverride > 0
                ? pageSizeOverride
                : (config != null ? config.productSearchPageSize() : 20);
        var reqBuilder = SearchRequest.newBuilder()
                .setPlacement(placement)
                .setBranch(branch)
                .setQuery(query)
                .setVisitorId(visitorId)
                .setPageSize(pageSize);
        if (filter != null && !filter.isBlank()) {
            reqBuilder.setFilter(filter);
        }
        if (pageToken != null && !pageToken.isBlank()) {
            reqBuilder.setPageToken(pageToken);
        }
        var req = reqBuilder.build();

        SearchServiceClient.SearchPagedResponse pagedResponse = client.search(req);
        var page = pagedResponse.getPage();
        String nextPageToken = pagedResponse.getNextPageToken() != null && !pagedResponse.getNextPageToken().isEmpty()
                ? pagedResponse.getNextPageToken() : null;
        long totalSize = -1;

        List<AgentResponse.ProductResult> results = new ArrayList<>();
        for (SearchResponse.SearchResult sr : page.getValues()) {
            var product = sr.getProduct();
            if (product == null) continue;
            String id = product.getName() != null ? product.getName() : "";
            String title = product.getTitle() != null ? product.getTitle() : "";
            String desc = product.getDescription() != null ? product.getDescription() : "";
            String price = "";
            if (product.hasPriceInfo()) {
                double p = product.getPriceInfo().getPrice();
                price = p > 0 ? String.format("$%.2f", p) : "";
            }
            String imageUri = null;
            if (product.getImagesCount() > 0) {
                var img = product.getImages(0);
                imageUri = img != null ? img.getUri() : null;
            }
            String gtin = product.getGtin() != null && !product.getGtin().isEmpty() ? product.getGtin() : null;
            String productId = product.getId() != null && !product.getId().isEmpty() ? product.getId() : null;
            List<String> categories = product.getCategoriesCount() > 0 ? new ArrayList<>(product.getCategoriesList()) : null;
            List<String> brands = product.getBrandsCount() > 0 ? new ArrayList<>(product.getBrandsList()) : null;
            String uri = product.getUri() != null && !product.getUri().isEmpty() ? product.getUri() : null;
            String availability = product.getAvailability() != Product.Availability.UNRECOGNIZED && product.getAvailability() != Product.Availability.AVAILABILITY_UNSPECIFIED
                    ? product.getAvailability().name() : null;
            List<String> sizes = product.getSizesCount() > 0 ? new ArrayList<>(product.getSizesList()) : null;
            List<String> materials = product.getMaterialsCount() > 0 ? new ArrayList<>(product.getMaterialsList()) : null;
            Map<String, Object> attributes = null;
            if (product.getAttributesCount() > 0) {
                attributes = new HashMap<>();
                for (Map.Entry<String, com.google.cloud.retail.v2beta.CustomAttribute> e : product.getAttributesMap().entrySet()) {
                    var v = e.getValue();
                    Object val = v.getTextCount() > 0 ? v.getText(0) : (v.getNumbersCount() > 0 ? v.getNumbers(0) : null);
                    if (val != null) attributes.put(e.getKey(), val);
                }
                if (attributes.isEmpty()) attributes = null;
            }
            results.add(new AgentResponse.ProductResult(id, title, desc, price, imageUri, gtin, productId, categories, brands, uri, availability, sizes, materials, attributes, false));
        }
        return SearchResult.of(results, nextPageToken, totalSize);
    }
}
