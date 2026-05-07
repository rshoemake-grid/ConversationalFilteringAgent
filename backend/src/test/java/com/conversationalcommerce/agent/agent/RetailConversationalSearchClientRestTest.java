package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RetailConversationalSearchClientRest response parsing.
 * Uses reflection to test parseResponse logic without making real HTTP calls.
 */
class RetailConversationalSearchClientRestTest {

    private RetailConversationalSearchClientRest client;

    @BeforeEach
    void setUp() {
        var config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setConversationalFilteringMode("ENABLED");
        var resolver = new BrandDisplayResolver(config, null);
        client = new RetailConversationalSearchClientRest(config, null, resolver);
    }

    @Test
    void parseResponse_extractsSuggestedAnswersFromConversationalFilteringResult() {
        String json = """
            {
              "conversationId": "conv-123",
              "refinedSearch": [{"query": "princess decorations"}],
              "userQueryTypes": ["SIMPLE_PRODUCT_SEARCH"],
              "conversationalFilteringResult": {
                "followupQuestion": "What type of decoration?",
                "suggestedAnswers": [
                  {"productAttributeValue": {"name": "attributes.type", "value": "Balloons"}},
                  {"productAttributeValue": {"name": "attributes.type", "value": "Streamers"}},
                  {"productAttributeValue": {"name": "attributes.type", "value": "Tablecloths"}}
                ]
              }
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactly("Balloons", "Streamers", "Tablecloths");
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("Balloons", "Streamers", "Tablecloths");
    }

    @Test
    void parseResponse_extractsSuggestedAnswersFromNestedFollowupQuestion() {
        String json = """
            {
              "conversationId": "conv-1",
              "conversationalFilteringResult": {
                "followupQuestion": {
                  "followupQuestion": "What size?",
                  "suggestedAnswers": [
                    {"productAttributeValue": {"value": "12oz"}},
                    {"productAttributeValue": {"value": "24oz"}}
                  ]
                }
              }
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactly("12oz", "24oz");
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("12oz", "24oz");
    }

    @Test
    void parseResponse_saysNoProductsFoundWhenSimpleProductSearchWithEmptyFollowup() {
        String json = """
            {
              "conversationId": "conv-1",
              "conversationalFilteringResult": {
                "followupQuestion": {}
              },
              "userQueryTypes": ["SIMPLE_PRODUCT_SEARCH"]
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.text()).isEqualTo("No products found.");
    }

    @Test
    void parseResponse_returnsDidNotUnderstandWhenUserQueryTypesIsRetailIrrelevant() {
        String json = """
            {
              "conversationId": "conv-1",
              "userQueryTypes": ["RETAIL_IRRELEVANT"]
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.text()).isEqualTo("I didn't understand your response.");
    }

    @Test
    void parseResponse_appendsFollowupQuestionWhenTextExists() {
        String json = """
            {
              "conversationId": "conv-1",
              "conversationalTextResponse": "Here are some options.",
              "conversationalFilteringResult": {
                "followupQuestion": "What type would you prefer?",
                "suggestedAnswers": [
                  {"productAttributeValue": {"value": "A"}},
                  {"productAttributeValue": {"value": "B"}}
                ]
              }
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.text()).isEqualTo("Here are some options.\n\nWhat type would you prefer?");
    }

    @Test
    void parseResponse_resolvesBrandDisplayTextFromCode() {
        String json = """
            {
              "conversationId": "conv-1",
              "conversationalFilteringResult": {
                "followupQuestion": "Which brand?",
                "suggestedAnswers": [
                  {"productAttributeValue": {"name": "attributes.brands", "value": "NIKE"}},
                  {"productAttributeValue": {"name": "attributes.brands", "value": "ADIDAS"}}
                ]
              }
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactly("Nike", "Adidas");
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("NIKE", "ADIDAS");
    }

    @Test
    void parseResponse_resolvesStorageTypeDisplayNamesForShortCodes() {
        var config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setConversationalFilteringMode("ENABLED");
        config.setAttributeDisplayMapping(java.util.Map.of(
                "storageType", java.util.Map.of("S", "Ambient", "R", "Refrigerated", "D", "Dry storage")
        ));
        var resolver = new BrandDisplayResolver(config, null);
        client = new RetailConversationalSearchClientRest(config, null, resolver);

        String json = """
            {
              "conversationId": "conv-1",
              "conversationalFilteringResult": {
                "followupQuestion": "What type of stock do you prefer?",
                "suggestedAnswers": [
                  {"productAttributeValue": {"value": "S"}},
                  {"productAttributeValue": {"value": "R"}}
                ]
              }
            }
            """;
        var result = invokeParseResponse(json);
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactly("Ambient", "Refrigerated");
        assertThat(result.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("S", "R");
    }

    private ConversationalCommerceClient.ConversationalCommerceResult invokeParseResponse(String json) {
        return (ConversationalCommerceClient.ConversationalCommerceResult)
                ReflectionTestUtils.invokeMethod(client, "parseResponse", json);
    }
}
