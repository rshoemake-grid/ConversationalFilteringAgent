package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.agent.AgentResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResponseTest {

    @Test
    void from_mapsAllFields() {
        var agentResponse = AgentResponse.builder()
                .text("Hello")
                .conversationId("conv-1")
                .refinedQuery("running shoes")
                .products(List.of(
                        AgentResponse.ProductResult.of("p1", "Nike Run", "Running shoes", "$99", "http://img/1")
                ))
                .build();

        ChatResponse response = ChatResponse.from(agentResponse);

        assertThat(response.text()).isEqualTo("Hello");
        assertThat(response.conversationId()).isEqualTo("conv-1");
        assertThat(response.refinedQuery()).isEqualTo("running shoes");
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).id()).isEqualTo("p1");
        assertThat(response.products().get(0).title()).isEqualTo("Nike Run");
        assertThat(response.products().get(0).description()).isEqualTo("Running shoes");
        assertThat(response.products().get(0).price()).isEqualTo("$99");
        assertThat(response.products().get(0).imageUri()).isEqualTo("http://img/1");
        assertThat(response.source()).isEqualTo("agent");
    }

    @Test
    void from_mapsSource() {
        var agentResponse = AgentResponse.builder()
                .text("App fallback")
                .conversationId("c1")
                .source("app")
                .build();

        ChatResponse response = ChatResponse.from(agentResponse);

        assertThat(response.source()).isEqualTo("app");
    }

    @Test
    void from_handlesNullProducts() {
        var agentResponse = AgentResponse.builder()
                .text("Hi")
                .conversationId("conv-2")
                .refinedQuery(null)
                .products(null)
                .build();

        ChatResponse response = ChatResponse.from(agentResponse);

        assertThat(response.text()).isEqualTo("Hi");
        assertThat(response.conversationId()).isEqualTo("conv-2");
        assertThat(response.refinedQuery()).isNull();
        assertThat(response.products()).isEmpty();
    }

    @Test
    void from_handlesNullAgentResponse() {
        ChatResponse response = ChatResponse.from(null);

        assertThat(response.text()).isEmpty();
        assertThat(response.conversationId()).isEmpty();
        assertThat(response.refinedQuery()).isNull();
        assertThat(response.products()).isEmpty();
    }

    @Test
    void from_handlesEmptyProducts() {
        var agentResponse = AgentResponse.builder()
                .text("No results")
                .conversationId("conv-3")
                .products(List.of())
                .build();

        ChatResponse response = ChatResponse.from(agentResponse);

        assertThat(response.products()).isEmpty();
    }
}
