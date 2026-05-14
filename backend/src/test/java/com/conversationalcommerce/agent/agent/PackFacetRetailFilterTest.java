package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PackFacetRetailFilterTest {

    @Test
    void buildOrClause_joinsAttributesWithOr() {
        String clause = PackFacetRetailFilter.buildOrClause("3__LB", List.of("packSize", "netWeight"));
        assertThat(clause).contains("attributes.packSize: ANY(\"3__LB\")");
        assertThat(clause).contains(" OR ");
        assertThat(clause).contains("attributes.netWeight: ANY(\"3__LB\")");
        assertThat(clause).startsWith("(");
        assertThat(clause).endsWith(")");
    }

    @Test
    void buildPackFacetFilter_whenChipSelected_returnsClause() {
        var ctx = Map.<String, Object>of(
                "previousSuggestedAnswers",
                List.of(Map.of("displayText", "3 lb", "value", "3__LB")));
        String f = PackFacetRetailFilter.buildPackFacetFilterIfSuggestionSelected(
                "3__LB",
                List.of(),
                ctx);
        assertThat(f).contains("attributes.packSize: ANY(\"3__LB\")");
        assertThat(f).doesNotContain("brands:");
    }

    @Test
    void buildPackFacetFilter_whenNotPackEncoded_returnsNull() {
        var ctx = Map.<String, Object>of(
                "previousSuggestedAnswers",
                List.of(Map.of("displayText", "Nike", "value", "NIKE")));
        assertThat(PackFacetRetailFilter.buildPackFacetFilterIfSuggestionSelected("NIKE", List.of(), ctx)).isNull();
    }

    @Test
    void buildPackFacetFilter_whenNoMatchingSelection_returnsNull() {
        var ctx = Map.<String, Object>of("previousSuggestedAnswers", List.of());
        assertThat(PackFacetRetailFilter.buildPackFacetFilterIfSuggestionSelected(
                "3__LB", List.of(new ConversationalCommerceClient.SuggestedAnswer("12 oz", "12__OZ")), ctx)).isNull();
    }
}
