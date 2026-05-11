package com.conversationalcommerce.agent.config;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.orchestration.AdkOrchestrator;
import com.conversationalcommerce.agent.orchestration.AdkVaisrSessionBridge;
import com.conversationalcommerce.agent.orchestration.VaisrRetailProductResolver;
import com.conversationalcommerce.agent.tool.ConversationalCommerceTool;
import com.conversationalcommerce.agent.tool.LoyaltyRecommendationTool;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Gemini;
import com.google.adk.tools.FunctionTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

@Configuration
public class AdkConfig {

    private static final Logger log = LoggerFactory.getLogger(AdkConfig.class);

    @Value("${app.gemini.model:gemini-2.5-flash}")
    private String model;

    private final ConversationalCommerceClient conversationalCommerceClient;
    private final ConversationalCommerceConfig config;
    private final Environment environment;
    private final GeminiClientFactory clientFactory;

    public AdkConfig(ConversationalCommerceClient conversationalCommerceClient,
                    ConversationalCommerceConfig config,
                    Environment environment,
                    GeminiClientFactory clientFactory) {
        this.conversationalCommerceClient = conversationalCommerceClient;
        this.config = config;
        this.environment = environment;
        this.clientFactory = clientFactory;
    }

    @PostConstruct
    void configureTool() {
        ConversationalCommerceTool.configure(
                conversationalCommerceClient,
                config.placement(),
                config.branch(),
                config.defaultVisitorId()
        );
    }

    @PostConstruct
    void warnIfGeminiUnavailable() {
        if (!GeminiRuntimeAuth.canUseGemini(environment)) {
            log.warn("Gemini not configured. Approach B (ADK orchestrator) will return placeholder responses. "
                    + "Set app.gemini.api-key or GOOGLE_API_KEY, or set GCP_PROJECT_ID / app.gemini.vertex-project "
                    + "for Vertex AI with Application Default Credentials.");
        }
    }

    @Bean
    @ConditionalOnExpression("T(com.conversationalcommerce.agent.config.GeminiRuntimeAuth).canUseGemini(@environment)")
    public LlmAgent adkOrchestratorAgent() throws Exception {
        var client = clientFactory.buildClientForGemini(environment);

        FunctionTool searchProductsTool = FunctionTool.create(
                ConversationalCommerceTool.class, "searchProducts");
        FunctionTool loyaltyTool = FunctionTool.create(
                LoyaltyRecommendationTool.class, "getLoyaltyRecommendations");

        var geminiModel = Gemini.builder()
                .modelName(model)
                .apiClient(client)
                .build();

        return LlmAgent.builder()
                .model(geminiModel)
                .name("adk_orchestrator")
                .instruction("""
                    You are a shopping assistant orchestrator. You help users find products using the searchProducts tool.

                    Prefer calling searchProducts over giving generic product education from memory. Do not list product types,
                    brands, or package ideas from your training data when the user is trying to shop—use the tool and
                    summarize what the store returns.

                    When searchProducts returns suggestedAnswers (from the retail catalog), each entry has displayText and value.
                    Do not invent different product attributes or options. Do NOT list, bullet, or repeat those option labels
                    in your reply text—the client's UI already shows them as clickable chips. Respond with at most one short
                    sentence, typically the clarifying question ending in ?, and nothing else.

                    If the user's request is vague (e.g. "show me products", "shoes", "browse"), ask at most one short
                    clarifying question, then search. If they answer with no preference—"any", "either", "no preference",
                    "doesn't matter", "you choose"—call searchProducts immediately with the topic you already have
                    (e.g. "rice", "running shoes") and do not ask again or lecture about options.

                    When the user gives a specific query, use searchProducts right away.
                    When the user asks about rewards, loyalty, or recommendations, use getLoyaltyRecommendations.
                    When the user asks only about store hours or policies (not products), answer briefly from your knowledge.
                    Pass the conversationId from the previous searchProducts response on follow-up calls. After the user picks a
                    stock/storage chip (Ambient, Refrigerated, Dry storage, etc.), call searchProducts with the same catalog
                    conversationId—do not answer with generic product education from training; the storefront searchProducts
                    result drives the next step.

                    If search results are returned, summarize them for the user.
                    """)
                .description("Orchestrates product search, loyalty info, and general shopping assistance")
                .tools(searchProductsTool, loyaltyTool)
                .build();
    }

    @Bean
    public AdkOrchestrator adkOrchestrator(
            @Autowired(required = false) @Qualifier("adkOrchestratorAgent") LlmAgent adkOrchestratorAgent,
            AdkVaisrSessionBridge adkVaisrSessionBridge,
            VaisrRetailProductResolver vaisrRetailProductResolver) {
        return new AdkOrchestrator(adkOrchestratorAgent, null, adkVaisrSessionBridge, vaisrRetailProductResolver, config);
    }
}
