package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RetailSearchClientRest response parsing.
 * Uses reflection to test parseResponse without real HTTP calls.
 */
class RetailSearchClientRestTest {

    @Test
    void parseResponse_extractsGtinAndAllProductFields() {
        String json = """
            {
              "results": [
                {
                  "product": {
                    "name": "projects/p/locations/global/catalogs/default_catalog/branches/default_branch/products/sku-123",
                    "id": "sku-123",
                    "title": "Nike Air Max",
                    "description": "Running shoes",
                    "gtin": "045496590417",
                    "categories": ["Shoes > Athletic"],
                    "brands": ["Nike"],
                    "uri": "https://example.com/product/123",
                    "availability": "IN_STOCK",
                    "sizes": ["10", "11", "12"],
                    "materials": ["Synthetic"],
                    "priceInfo": {"price": 129.99},
                    "images": [{"uri": "https://example.com/img.png"}],
                    "attributes": {
                      "color": {"text": ["Black"]},
                      "upc": {"text": ["045496590417"]}
                    }
                  }
                }
              ]
            }
            """;
        var client = new RetailSearchClientRest((GcpCredentialsProvider) null, new ConversationalCommerceConfig());
        @SuppressWarnings("unchecked")
        List<AgentResponse.ProductResult> results = (List<AgentResponse.ProductResult>)
                ReflectionTestUtils.invokeMethod(client, "parseResponse", json);

        assertThat(results).hasSize(1);
        var p = results.get(0);
        assertThat(p.id()).contains("products/sku-123");
        assertThat(p.title()).isEqualTo("Nike Air Max");
        assertThat(p.description()).isEqualTo("Running shoes");
        assertThat(p.price()).isEqualTo("$129.99");
        assertThat(p.imageUri()).isEqualTo("https://example.com/img.png");
        assertThat(p.gtin()).isEqualTo("045496590417");
        assertThat(p.productId()).isEqualTo("sku-123");
        assertThat(p.categories()).containsExactly("Shoes > Athletic");
        assertThat(p.brands()).containsExactly("Nike");
        assertThat(p.uri()).isEqualTo("https://example.com/product/123");
        assertThat(p.availability()).isEqualTo("IN_STOCK");
        assertThat(p.sizes()).containsExactly("10", "11", "12");
        assertThat(p.materials()).containsExactly("Synthetic");
        assertThat(p.attributes()).containsEntry("color", "Black").containsEntry("upc", "045496590417");
    }

    @Test
    void parseResponse_handlesMinimalProduct() {
        String json = """
            {
              "results": [
                {
                  "product": {
                    "name": "projects/p/products/minimal",
                    "title": "Basic Product"
                  }
                }
              ]
            }
            """;
        var client = new RetailSearchClientRest((GcpCredentialsProvider) null, new ConversationalCommerceConfig());
        @SuppressWarnings("unchecked")
        List<AgentResponse.ProductResult> results = (List<AgentResponse.ProductResult>)
                ReflectionTestUtils.invokeMethod(client, "parseResponse", json);

        assertThat(results).hasSize(1);
        var p = results.get(0);
        assertThat(p.id()).contains("products/minimal");
        assertThat(p.title()).isEqualTo("Basic Product");
        assertThat(p.description()).isEmpty();
        assertThat(p.price()).isEmpty();
        assertThat(p.imageUri()).isNull();
        assertThat(p.gtin()).isNull();
        assertThat(p.productId()).isEqualTo("minimal");
        assertThat(p.categories()).isNull();
        assertThat(p.brands()).isNull();
    }

    @Test
    void parseResponse_usesVariantRollupPriceWhenProductPriceMissing() {
        String json = """
            {
              "results": [
                {
                  "id": "projects/p/products/6052075",
                  "product": {
                    "name": "projects/p/locations/global/catalogs/default_catalog/branches/1/products/6052075",
                    "title": "Medium Grain Rice, 5 pounds",
                    "images": [{"uri": "https://example.com/rice.png"}],
                    "categories": ["Canned & Dry > Pasta and Rice"],
                    "brands": ["LA PREF"]
                  },
                  "variantRollupValues": {
                    "price": {"numberValue": 8.99}
                  }
                }
              ]
            }
            """;
        var client = new RetailSearchClientRest((GcpCredentialsProvider) null, new ConversationalCommerceConfig());
        @SuppressWarnings("unchecked")
        List<AgentResponse.ProductResult> results = (List<AgentResponse.ProductResult>)
                ReflectionTestUtils.invokeMethod(client, "parseResponse", json);

        assertThat(results).hasSize(1);
        var p = results.get(0);
        assertThat(p.price()).isEqualTo("$8.99");
        assertThat(p.productId()).isEqualTo("6052075");
    }

    @Test
    void parseResponse_usesFirstVariantPriceWhenProductAndRollupMissing() {
        String json = """
            {
              "results": [
                {
                  "product": {
                    "name": "projects/p/products/varied",
                    "title": "T-Shirt",
                    "variants": [
                      {
                        "priceInfo": {"price": 24.50},
                        "description": "Blue cotton tee"
                      }
                    ]
                  }
                }
              ]
            }
            """;
        var client = new RetailSearchClientRest((GcpCredentialsProvider) null, new ConversationalCommerceConfig());
        @SuppressWarnings("unchecked")
        List<AgentResponse.ProductResult> results = (List<AgentResponse.ProductResult>)
                ReflectionTestUtils.invokeMethod(client, "parseResponse", json);

        assertThat(results).hasSize(1);
        var p = results.get(0);
        assertThat(p.price()).isEqualTo("$24.50");
        assertThat(p.description()).isEqualTo("Blue cotton tee");
        assertThat(p.productId()).isEqualTo("varied");
    }
}
