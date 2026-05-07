package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.orchestration.OrchestratorService;
import com.conversationalcommerce.agent.web.ChatRequest.OrchestrationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest {

    private MockMvc mockMvc;
    private StubOrchestratorService stubOrchestratorService;

    @BeforeEach
    void setUp() {
        stubOrchestratorService = new StubOrchestratorService();
        var controller = new ChatController(stubOrchestratorService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void chat_returnsOkWithResponse() throws Exception {
        stubOrchestratorService.setNextResponse(AgentResponse.builder()
                .text("Hi there!")
                .conversationId("conv-1")
                .refinedQuery(null)
                .products(List.of())
                .build());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "convo_commerce",
                                  "message": "hello",
                                  "conversationId": "conv-1",
                                  "sessionId": "sess-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hi there!"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));
    }

    @Test
    void chat_generatesSessionIdWhenNotProvided() throws Exception {
        stubOrchestratorService.setNextResponse(AgentResponse.builder()
                .text("Here are some shoes")
                .conversationId("new-session")
                .build());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "adk_orchestrator",
                                  "message": "search shoes"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Here are some shoes"));
    }

    @Test
    void chat_returns400WhenModeMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void chat_returns400WhenMessageBlank() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "convo_commerce",
                                  "message": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void chat_returnsProductsInResponse() throws Exception {
        var productList = List.of(
                AgentResponse.ProductResult.of("p1", "Rice 1", "White rice", "$5", null),
                AgentResponse.ProductResult.of("p2", "Rice 2", "Brown rice", "$6", null)
        );
        stubOrchestratorService.setNextResponse(AgentResponse.builder()
                .text("I found 2 products matching your request.")
                .conversationId("conv-1")
                .refinedQuery("rice")
                .products(productList)
                .source("app")
                .queryType("RETAIL_IRRELEVANT")
                .build());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "convo_commerce",
                                  "message": "Any",
                                  "conversationId": "conv-1",
                                  "sessionId": "sess-1",
                                  "previousRefinedQuery": "rice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("I found 2 products matching your request."))
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].title").value("Rice 1"))
                .andExpect(jsonPath("$.products[1].title").value("Rice 2"));
    }

    @Test
    void chat_returns500WithProblemDetailWhenOrchestratorThrows() throws Exception {
        stubOrchestratorService.setThrowOnNext(true);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "convo_commerce",
                                  "message": "hello"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("Simulated failure"));
    }

    private static class StubOrchestratorService extends OrchestratorService {
        private AgentResponse nextResponse;
        private boolean throwOnNext;

        StubOrchestratorService() {
            super(null, null);
        }

        void setNextResponse(AgentResponse r) {
            this.nextResponse = r;
        }

        void setThrowOnNext(boolean b) {
            this.throwOnNext = b;
        }

        @Override
        public AgentResponse process(OrchestrationMode mode, String message, String conversationId, String sessionId,
                                     String imageBase64, Integer maxSuggestedAnswers, String previousAssistantText,
                                     List<ChatRequest.SuggestedAnswerInput> previousSuggestedAnswers,
                                     String previousRefinedQuery, String productPageToken, String previousProductFilter,
                                     Integer productPageSize) {
            if (throwOnNext) {
                throw new RuntimeException("Simulated failure");
            }
            return nextResponse != null ? nextResponse : AgentResponse.builder().text("").conversationId("").build();
        }
    }
}
