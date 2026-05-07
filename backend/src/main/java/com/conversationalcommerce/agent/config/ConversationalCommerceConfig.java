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
    /** Max products per search request (REST and gRPC). Default 20. */
    private int productSearchPageSize = 20;
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

    public int productSearchPageSize() {
        return productSearchPageSize > 0 ? productSearchPageSize : 20;
    }
    public void setProductSearchPageSize(int v) {
        this.productSearchPageSize = v > 0 ? v : 20;
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
