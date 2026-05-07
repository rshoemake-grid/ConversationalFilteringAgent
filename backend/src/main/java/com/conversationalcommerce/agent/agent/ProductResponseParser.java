package com.conversationalcommerce.agent.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses GCP Retail Product JSON into ProductResult fields.
 * Used by both search results and Product.Get response.
 */
final class ProductResponseParser {

    private ProductResponseParser() {}

    static AgentResponse.ProductResult fromProductMap(Map<String, Object> product, Map<String, Object> searchResult, boolean detailsFetched) {
        if (product == null) return null;
        String id = nullToEmpty(getString(product, "name"));
        String title = nullToEmpty(getString(product, "title"));
        String desc = parseDescription(product);
        String price = parsePrice(product, searchResult);
        String imageUri = parseFirstImageUri(product);
        String gtin = getString(product, "gtin");
        String productId = parseProductId(product);
        List<String> categories = getStringList(product, "categories");
        List<String> brands = getStringList(product, "brands");
        String uri = getString(product, "uri");
        String availability = product.containsKey("availability") ? String.valueOf(product.get("availability")) : null;
        List<String> sizes = getStringList(product, "sizes");
        List<String> materials = getStringList(product, "materials");
        Map<String, Object> attributes = parseAttributes(product);
        return new AgentResponse.ProductResult(
                id, title, desc, price, imageUri,
                gtin, productId, categories, brands, uri, availability, sizes, materials, attributes, detailsFetched);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String getString(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) return null;
        Object v = map.get(key);
        return v != null ? String.valueOf(v).trim() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        if (!map.containsKey(key) || !(map.get(key) instanceof List<?> list)) return null;
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) out.add(String.valueOf(item).trim());
        }
        return out.isEmpty() ? null : out;
    }

    private static String parseProductId(Map<String, Object> product) {
        String id = getString(product, "id");
        if (id != null && !id.isBlank()) return id;
        String name = getString(product, "name");
        if (name == null || name.isBlank()) return null;
        int lastSlash = name.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < name.length() - 1 ? name.substring(lastSlash + 1) : null;
    }

    @SuppressWarnings("unchecked")
    private static String parseDescription(Map<String, Object> product) {
        String desc = getString(product, "description");
        if (desc != null && !desc.isBlank()) return desc;
        if (product.containsKey("variants") && product.get("variants") instanceof List<?> variants && !variants.isEmpty()) {
            Object first = variants.get(0);
            if (first instanceof Map<?, ?> v && v.containsKey("description"))
                return nullToEmpty(getString((Map<String, Object>) v, "description"));
        }
        return "";
    }

    private static String parsePrice(Map<String, Object> product, Map<String, Object> searchResult) {
        String fromProduct = parsePriceFromProduct(product);
        if (!fromProduct.isEmpty()) return fromProduct;
        if (searchResult != null) {
            String fromRollup = parsePriceFromVariantRollup(searchResult);
            if (!fromRollup.isEmpty()) return fromRollup;
        }
        return parsePriceFromFirstVariant(product);
    }

    private static String parsePriceFromProduct(Map<String, Object> product) {
        if (!product.containsKey("priceInfo")) return "";
        Object pi = product.get("priceInfo");
        if (!(pi instanceof Map<?, ?> priceInfo)) return "";
        Object p = ((Map<?, ?>) priceInfo).get("price");
        if (p instanceof Number num && num.doubleValue() > 0) {
            return String.format("$%.2f", num.doubleValue());
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String parsePriceFromVariantRollup(Map<String, Object> searchResult) {
        if (!searchResult.containsKey("variantRollupValues") || !(searchResult.get("variantRollupValues") instanceof Map<?, ?> rollup))
            return "";
        Object priceVal = ((Map<String, Object>) rollup).get("price");
        if (priceVal == null) return "";
        if (priceVal instanceof Number num && num.doubleValue() > 0)
            return String.format("$%.2f", num.doubleValue());
        if (priceVal instanceof Map<?, ?> m) {
            if (m.containsKey("numberValue")) {
                Object n = m.get("numberValue");
                if (n instanceof Number num && num.doubleValue() > 0)
                    return String.format("$%.2f", num.doubleValue());
            }
            if (m.containsKey("listValue") && m.get("listValue") instanceof Map<?, ?> listMap
                    && listMap.containsKey("values") && listMap.get("values") instanceof List<?> values && !values.isEmpty()) {
                Object first = values.get(0);
                if (first instanceof Map<?, ?> vm && vm.containsKey("numberValue")) {
                    Object n = vm.get("numberValue");
                    if (n instanceof Number num && num.doubleValue() > 0)
                        return String.format("$%.2f", num.doubleValue());
                }
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String parsePriceFromFirstVariant(Map<String, Object> product) {
        if (!product.containsKey("variants") || !(product.get("variants") instanceof List<?> variants) || variants.isEmpty())
            return "";
        Object first = variants.get(0);
        if (!(first instanceof Map<?, ?> v)) return "";
        return parsePriceFromProduct((Map<String, Object>) v);
    }

    @SuppressWarnings("unchecked")
    private static String parseFirstImageUri(Map<String, Object> product) {
        if (!product.containsKey("images") || !(product.get("images") instanceof List<?> images) || images.isEmpty())
            return null;
        Object img = images.get(0);
        if (img instanceof Map<?, ?> imgMap && imgMap.containsKey("uri")) {
            return String.valueOf(imgMap.get("uri"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseAttributes(Map<String, Object> product) {
        if (!product.containsKey("attributes") || !(product.get("attributes") instanceof Map<?, ?> attrs))
            return null;
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> e : ((Map<String, Object>) attrs).entrySet()) {
            Object val = e.getValue();
            if (val instanceof Map<?, ?> m) {
                if (m.containsKey("text") && m.get("text") instanceof List<?> textList && !textList.isEmpty()) {
                    out.put(e.getKey(), String.valueOf(textList.get(0)));
                } else if (m.containsKey("numbers") && m.get("numbers") instanceof List<?> numList && !numList.isEmpty()) {
                    out.put(e.getKey(), numList.get(0));
                }
            }
        }
        return out.isEmpty() ? null : out;
    }
}
