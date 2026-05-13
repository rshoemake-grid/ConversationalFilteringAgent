package com.conversationalcommerce.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "conversational-commerce")
public class ConversationalCommerceConfig {

    private String projectId = "";
    private String placement = "";
    private String branch = "";
    private String defaultVisitorId = "default-visitor";
    /** "rest" (default) or "grpc" - rest bypasses ALPN/VPN issues */
    private String transport = "rest";
    /** "DISABLED" (default), "ENABLED", or "CONVERSATIONAL_FILTER_ONLY". ENABLED lets the agent ask follow-up questions. */
    private String conversationalFilteringMode = "DISABLED";
    /** Filter attribute for stock/storage type (GCP often returns attributes.stockType). Use "stockType" or "storageType". */
    private String stockTypeFilterAttribute = "stockType";
    /**
     * When true, Retail {@code ANY("…")} uses AMBIENT / REFRIGERATED / DRY_STORAGE for S/R/D (typical GCP enum values).
     * Default true; set false if {@code attributes.stockType} is indexed as single letters only.
     */
    private boolean stockTypeFilterUseCanonicalValues = true;
    /** Used for UI heuristics (approximate totals, chip suppression). Not used for Retail listing continuation. */
    private int productSearchPageSize = 20;
    /**
     * Page size for each Retail request when populating the <strong>initial</strong> catalog listing (first fetch,
     * not in-memory pool refinement). Capped at 100 (typical Retail API limit). Default 100 for PoC.
     */
    private int initialCatalogPageSize = 100;
    /**
     * When true, follows {@code nextPageToken} until exhausted or caps below are hit, then returns one combined list.
     * PoC default true so the first search maximizes catalog coverage without UI pagination.
     */
    private boolean initialCatalogFetchAllPages = true;
    /** Stop aggregating after this many products (safety). Default 1000. */
    private int initialCatalogMaxProducts = 1000;
    /** Maximum Retail list requests per aggregated initial search (safety). Default 100. */
    private int initialCatalogMaxPageRequests = 100;
    /**
     * When true, initial catalog responses omit {@code nextPageToken} so clients page {@code products} locally (no Retail listing continuation). Default true.
     */
    private boolean initialCatalogSuppressNextPageToken = true;
    /**
     * When true, initial catalog merge uses Retail {@code offset} to request pages in parallel up to
     * {@link #initialCatalogMaxPageRequests()} and {@link #initialCatalogMaxProducts()}. When false,
     * uses sequential {@code nextPageToken} walks (slower but matches token-based paging exactly).
     */
    private boolean initialCatalogParallelPageFetches = true;
    /**
     * PoC: after the first initial catalog listing phase for a conversation (or session if no conversation id),
     * block further Retail Search listing and brand display lookup via Search. Product.get enrichment and
     * conversational search are unchanged. Default true.
     */
    private boolean retailSingleShotPerConversation = true;
    /** Max concurrent Retail search calls during parallel initial catalog fetch. Default 8. */
    private int initialCatalogMaxConcurrentRequests = 8;
    /** Above this count we ask user to narrow (or show products if in recovery). Default 50 to avoid hiding results. */
    private int productCountThreshold = 50;
    /** Map attribute name -> (value -> display name) for suggested answers. E.g. brands: {NIKE: Nike, ADIDAS: Adidas} */
    private java.util.Map<String, java.util.Map<String, String>> attributeDisplayMapping = new java.util.HashMap<>();
    /** Map attribute name -> (short/user input -> canonical API value). Expands before sending to GCP when short codes cause RETAIL_IRRELEVANT. */
    private java.util.Map<String, java.util.Map<String, String>> attributeValueExpansion = new java.util.HashMap<>();

    public String projectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId != null ? projectId : ""; }

    public String placement() { return placement; }
    public void setPlacement(String placement) { this.placement = placement != null ? placement : ""; }

    public String branch() { return branch; }
    public void setBranch(String branch) { this.branch = branch != null ? branch : ""; }

    public String defaultVisitorId() { return defaultVisitorId; }
    public void setDefaultVisitorId(String defaultVisitorId) { this.defaultVisitorId = defaultVisitorId != null ? defaultVisitorId : "default-visitor"; }

    public String transport() { return transport != null ? transport : "rest"; }
    public void setTransport(String transport) { this.transport = transport != null ? transport : "rest"; }

    public String conversationalFilteringMode() {
        return conversationalFilteringMode != null ? conversationalFilteringMode : "DISABLED";
    }
    public void setConversationalFilteringMode(String mode) {
        this.conversationalFilteringMode = mode != null ? mode : "DISABLED";
    }

    public String stockTypeFilterAttribute() {
        return stockTypeFilterAttribute != null && !stockTypeFilterAttribute.isBlank() ? stockTypeFilterAttribute : "stockType";
    }
    public void setStockTypeFilterAttribute(String v) {
        this.stockTypeFilterAttribute = v != null ? v : "stockType";
    }

    public boolean stockTypeFilterUseCanonicalValues() {
        return stockTypeFilterUseCanonicalValues;
    }

    public void setStockTypeFilterUseCanonicalValues(boolean stockTypeFilterUseCanonicalValues) {
        this.stockTypeFilterUseCanonicalValues = stockTypeFilterUseCanonicalValues;
    }

    public int productSearchPageSize() {
        return productSearchPageSize > 0 ? productSearchPageSize : 20;
    }
    public void setProductSearchPageSize(int v) {
        this.productSearchPageSize = v > 0 ? v : 20;
    }

    public int initialCatalogPageSize() {
        int v = initialCatalogPageSize > 0 ? initialCatalogPageSize : 100;
        return Math.min(v, 100);
    }

    public void setInitialCatalogPageSize(int v) {
        this.initialCatalogPageSize = v;
    }

    public boolean initialCatalogFetchAllPages() {
        return initialCatalogFetchAllPages;
    }

    public void setInitialCatalogFetchAllPages(boolean initialCatalogFetchAllPages) {
        this.initialCatalogFetchAllPages = initialCatalogFetchAllPages;
    }

    public int initialCatalogMaxProducts() {
        return initialCatalogMaxProducts > 0 ? initialCatalogMaxProducts : 1000;
    }

    public void setInitialCatalogMaxProducts(int v) {
        this.initialCatalogMaxProducts = v;
    }

    public int initialCatalogMaxPageRequests() {
        return initialCatalogMaxPageRequests > 0 ? initialCatalogMaxPageRequests : 100;
    }

    public void setInitialCatalogMaxPageRequests(int v) {
        this.initialCatalogMaxPageRequests = v;
    }

    public boolean initialCatalogSuppressNextPageToken() {
        return initialCatalogSuppressNextPageToken;
    }

    public void setInitialCatalogSuppressNextPageToken(boolean v) {
        this.initialCatalogSuppressNextPageToken = v;
    }

    public boolean initialCatalogParallelPageFetches() {
        return initialCatalogParallelPageFetches;
    }

    public void setInitialCatalogParallelPageFetches(boolean initialCatalogParallelPageFetches) {
        this.initialCatalogParallelPageFetches = initialCatalogParallelPageFetches;
    }

    public boolean retailSingleShotPerConversation() {
        return retailSingleShotPerConversation;
    }

    public void setRetailSingleShotPerConversation(boolean retailSingleShotPerConversation) {
        this.retailSingleShotPerConversation = retailSingleShotPerConversation;
    }

    public int initialCatalogMaxConcurrentRequests() {
        int v = initialCatalogMaxConcurrentRequests > 0 ? initialCatalogMaxConcurrentRequests : 8;
        return Math.min(64, v);
    }

    public void setInitialCatalogMaxConcurrentRequests(int initialCatalogMaxConcurrentRequests) {
        this.initialCatalogMaxConcurrentRequests = initialCatalogMaxConcurrentRequests;
    }

    public int productCountThreshold() {
        return productCountThreshold > 0 ? productCountThreshold : 50;
    }
    public void setProductCountThreshold(int v) {
        this.productCountThreshold = v > 0 ? v : 50;
    }

    public java.util.Map<String, java.util.Map<String, String>> getAttributeDisplayMapping() {
        return attributeDisplayMapping != null ? attributeDisplayMapping : new java.util.HashMap<>();
    }
    public void setAttributeDisplayMapping(java.util.Map<String, java.util.Map<String, String>> mapping) {
        this.attributeDisplayMapping = mapping != null ? mapping : new java.util.HashMap<>();
    }

    public java.util.Map<String, java.util.Map<String, String>> getAttributeValueExpansion() {
        return attributeValueExpansion != null ? attributeValueExpansion : new java.util.HashMap<>();
    }
    public void setAttributeValueExpansion(java.util.Map<String, java.util.Map<String, String>> expansion) {
        this.attributeValueExpansion = expansion != null ? expansion : new java.util.HashMap<>();
    }
}
