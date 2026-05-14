package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.orchestration.RetailProductApiGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves attribute values (e.g. brand codes) to display names. Uses config mapping first,
 * then common pack/UOM formatting (e.g. {@code 3__LB} → {@code 3 lb}), then runtime lookup via Retail Search API,
 * then title-case fallback.
 */
@Component
public class BrandDisplayResolver {

    private static final Logger log = LoggerFactory.getLogger(BrandDisplayResolver.class);

    private final ConversationalCommerceConfig config;
    private final RetailSearchClient searchClient;
    private final RetailProductApiGate retailProductApiGate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public BrandDisplayResolver(ConversationalCommerceConfig config,
                                RetailSearchClient searchClient,
                                RetailProductApiGate retailProductApiGate) {
        this.config = config;
        this.searchClient = searchClient;
        this.retailProductApiGate = retailProductApiGate;
    }

    /**
     * Resolve display text for an attribute value. Priority: config mapping → API lookup → title-case.
     *
     * @param attributeName raw name from API (e.g. "attributes.brandId", "attributes.brands")
     * @param value         raw value (e.g. "NIKE", "BHB/NPM")
     * @return display text, or original value if not a brand-like attribute
     */
    public String resolveDisplayText(String attributeName, String value) {
        return resolveDisplayText(attributeName, value, null);
    }

    /**
     * Resolve display text with optional search hint (e.g. refinedQuery "beef") to improve product lookup.
     */
    public String resolveDisplayText(String attributeName, String value, String searchQueryHint) {
        if (value == null || value.isBlank()) return value;
        String attrKeyOriginal = attributeName != null ? attributeName.replaceFirst("^attributes\\.", "") : "";
        String attrKey = attrKeyOriginal != null ? attrKeyOriginal.toLowerCase() : "";

        // 1. Config mapping (by attribute name). Treat stockType as storageType (GCP uses attributes.stockType).
        var mapping = config.getAttributeDisplayMapping();
        if (attrKey != null && !attrKey.isEmpty() && mapping != null) {
            String lookupKey = "stockType".equalsIgnoreCase(attrKey) ? "storageType" : attrKey;
            for (var entry : mapping.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(lookupKey)) {
                    var valueMap = entry.getValue();
                    if (valueMap != null && valueMap.containsKey(value)) {
                        String mapped = valueMap.get(value);
                        if (mapped != null && !mapped.isEmpty()) return mapped;
                    }
                    break;
                }
            }
        }

        // 1b. When no attribute name, try storageType mapping for S/R/D/N/F/C (common stock-type short codes)
        if ((attrKey == null || attrKey.isEmpty()) && mapping != null) {
            var storageMap = mapping.get("storageType");
            if (storageMap != null && storageMap.containsKey(value)) {
                String mapped = storageMap.get(value);
                if (mapped != null && !mapped.isEmpty()) return mapped;
            }
        }

        String packOrUom = formatNumericDoubleUnderscoreUom(value.trim());
        if (packOrUom != null) {
            return packOrUom;
        }

        // 2. Only try API lookup for brand-like attributes
        if (attrKey == null || !attrKey.contains("brand")) {
            return value;
        }

        // 3. Cache check
        String cacheKey = attrKey + ":" + value;
        String cached = cache.get(cacheKey);
        if (cached != null) return cached;

        if (!retailProductApiGate.mayQueryRetailSearchApis()) {
            return toTitleCase(value);
        }

        // 4. API lookup via product search
        if (searchClient != null && config.placement() != null && !config.placement().isEmpty()
                && config.branch() != null && !config.branch().isEmpty()) {
            String query = (searchQueryHint != null && !searchQueryHint.isBlank()) ? searchQueryHint : value;
            var filters = buildFilterVariants(attrKeyOriginal, value);
            for (String filter : filters) {
                try {
                    List<AgentResponse.ProductResult> results = searchClient.search(
                            config.placement(),
                            config.branch(),
                            query,  // use refinedQuery (e.g. "beef") or value; "*" often returns nothing
                            config.defaultVisitorId(),
                            filter
                    );
                    if (results != null && !results.isEmpty()) {
                        var product = results.get(0);
                        List<String> brands = product.brands();
                        if (brands != null && !brands.isEmpty()) {
                            String display = brands.get(0).trim();
                            if (!display.isEmpty()) {
                                cache.put(cacheKey, display);
                                return display;
                            }
                        }
                        // Product has no brands field; try custom attribute value from product
                        Map<String, Object> attrs = product.attributes();
                        if (attrs != null && (attrs.containsKey(attrKey) || attrs.containsKey(attrKeyOriginal))) {
                            Object av = attrs.get(attrKeyOriginal) != null ? attrs.get(attrKeyOriginal) : attrs.get(attrKey);
                            if (av != null) {
                                String display = String.valueOf(av).trim();
                                if (!display.isEmpty() && !display.equalsIgnoreCase(value)) {
                                    cache.put(cacheKey, display);
                                    return display;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Brand lookup failed for {}={} filter={}: {}", attributeName, value, filter, e.getMessage());
                }
            }
            log.info("Brand display lookup found no product for attribute {} value \"{}\"; using title-case fallback. " +
                    "Add config mapping or ensure products have Product.brands set.", attributeName, value);
        }

        // 5. Title-case fallback for brand-like attributes
        return toTitleCase(value);
    }

    /**
     * Catalog / VAISR often encodes pack or weight as {@code 3__LB} or {@code 12.5__OZ}. Returns a short label
     * for chip text, or null when the value does not match that pattern.
     */
    public static String formatNumericDoubleUnderscoreUom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        int sep = v.indexOf("__");
        if (sep <= 0 || sep >= v.length() - 2) {
            return null;
        }
        String numPart = v.substring(0, sep);
        String unitPart = v.substring(sep + 2);
        if (!numPart.matches("\\d+(?:\\.\\d+)?") || !unitPart.matches("[A-Za-z][A-Za-z0-9_]*")) {
            return null;
        }
        return numPart + " " + humanizeUomToken(unitPart);
    }

    private static String humanizeUomToken(String rawUnit) {
        String u = rawUnit.toUpperCase(Locale.ROOT).replace("_", "");
        return switch (u) {
            case "LB", "LBS" -> "lb";
            case "OZ" -> "oz";
            case "KG" -> "kg";
            case "G", "GM", "GRAM", "GRAMS" -> "g";
            case "CT", "CNT", "COUNT" -> "ct";
            case "PK", "PACK" -> "pk";
            case "EA", "EACH" -> "each";
            case "ML" -> "ml";
            case "L", "LT", "LTR" -> "L";
            case "GAL" -> "gal";
            default -> rawUnit.toLowerCase(Locale.ROOT).replace("_", " ");
        };
    }

    /** Try system brands first, then custom attribute. */
    private static List<String> buildFilterVariants(String attrKey, String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        if ("brands".equals(attrKey)) {
            return List.of("brands: ANY(\"" + escaped + "\")", "attributes.brands: ANY(\"" + escaped + "\")");
        }
        return List.of("attributes." + attrKey + ": ANY(\"" + escaped + "\")", "brands: ANY(\"" + escaped + "\")");
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
