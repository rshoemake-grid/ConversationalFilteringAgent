package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ClarifyingFollowUpPolicy;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Approach B: ADK agent as orchestrator.
 * ADK agent has tools including Conversational Commerce, delegates as needed.
 * When Gemini is not configured (no usable API key and no Vertex project for ADC), returns a placeholder response.
 * Bean is created by AdkConfig.
 */
public class AdkOrchestrator implements ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AdkOrchestrator.class);

    private static final String APP_NAME = "conversational_commerce";

    private static final ChatOrchestrator STUB_WHEN_NO_API_KEY = new StubOrchestrator();

    private static class StubOrchestrator implements ChatOrchestrator {
        @Override
        public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
            return AgentResponse.builder()
                    .text("Configure Gemini for Approach B (ADK orchestrator): set app.gemini.api-key or GOOGLE_API_KEY, "
                            + "or set GCP_PROJECT_ID or app.gemini.vertex-project and use Application Default Credentials "
                            + "(Vertex AI; e.g. gcloud auth application-default login).")
                    .conversationId(conversationId != null ? conversationId : "")
                    .build();
        }
    }

    private final LlmAgent adkOrchestratorAgent;
    private final InMemoryRunner runner;
    private final ChatOrchestrator testDelegate;
    private final AdkVaisrSessionBridge vaisrSessionBridge;
    private final VaisrRetailProductResolver vaisrRetailProductResolver;
    private final ConversationalCommerceConfig commerceConfig;

    public AdkOrchestrator(LlmAgent adkOrchestratorAgent) {
        this(adkOrchestratorAgent, adkOrchestratorAgent == null ? STUB_WHEN_NO_API_KEY : null, null, null, null);
    }

    /**
     * Package-private constructor for testing. When testDelegate is non-null, process() delegates to it.
     */
    AdkOrchestrator(LlmAgent adkOrchestratorAgent, ChatOrchestrator testDelegate) {
        this(adkOrchestratorAgent, testDelegate, null, null, null);
    }

    /**
     * Production constructor: full ADK runner plus VAISR session bridge and retail product resolution.
     */
    public AdkOrchestrator(
            LlmAgent adkOrchestratorAgent,
            ChatOrchestrator testDelegate,
            AdkVaisrSessionBridge vaisrSessionBridge,
            VaisrRetailProductResolver vaisrRetailProductResolver) {
        this(adkOrchestratorAgent, testDelegate, vaisrSessionBridge, vaisrRetailProductResolver, null);
    }

    /**
     * Full production constructor including commerce config (for clarifying / suggestion policy).
     */
    public AdkOrchestrator(
            LlmAgent adkOrchestratorAgent,
            ChatOrchestrator testDelegate,
            AdkVaisrSessionBridge vaisrSessionBridge,
            VaisrRetailProductResolver vaisrRetailProductResolver,
            ConversationalCommerceConfig commerceConfig) {
        this.adkOrchestratorAgent = adkOrchestratorAgent;
        this.runner = testDelegate != null ? null : new InMemoryRunner(adkOrchestratorAgent, APP_NAME);
        this.testDelegate = testDelegate;
        this.vaisrSessionBridge = vaisrSessionBridge;
        this.vaisrRetailProductResolver = vaisrRetailProductResolver;
        this.commerceConfig = commerceConfig;
    }

    public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
        if (testDelegate != null) {
            return testDelegate.process(message, conversationId, context);
        }
        String visitorId = ContextUtils.getVisitorId(context, null);

        String sessionId = resolveOrCreateSession(conversationId, visitorId);
        Content userMessage = Content.fromParts(Part.fromText(message));

        List<String> responseParts = new ArrayList<>();
        List<ConversationalCommerceClient.SuggestedAnswer> lastSearchSuggestions = new ArrayList<>();
        AtomicReference<Map<String, Object>> lastSuccessfulSearchTool = new AtomicReference<>();
        AtomicReference<String> searchProductsFailureDetail = new AtomicReference<>();

        try {
            if (vaisrSessionBridge != null) {
                AdkToolRequestContext.set(sessionId, visitorId, context, vaisrSessionBridge);
            }

            Flowable<Event> eventStream = runner.runAsync(visitorId, sessionId, userMessage);

            eventStream.blockingForEach(event -> {
                for (FunctionResponse fr : event.functionResponses()) {
                    if (fr.name().map(n -> n.contains("searchProducts")).orElse(false)) {
                        fr.response().ifPresent(resp -> {
                            String status = String.valueOf(resp.get("status"));
                            if ("error".equals(status)) {
                                String msg = Objects.toString(resp.get("message"), "");
                                searchProductsFailureDetail.set(msg);
                                log.warn("searchProducts tool error (sessionId={}): {}", sessionId, msg);
                            } else if ("success".equals(status)) {
                                replaceSuggestedAnswersFromToolMap(resp, lastSearchSuggestions);
                                lastSuccessfulSearchTool.set(new LinkedHashMap<>(resp));
                                String gcpConv = Objects.toString(resp.get("conversationId"), "").trim();
                                if (vaisrSessionBridge != null && !gcpConv.isEmpty()) {
                                    vaisrSessionBridge.setGcpConversationId(sessionId, gcpConv);
                                }
                            }
                        });
                    }
                }
                if (event.finalResponse()) {
                    String content = event.stringifyContent();
                    if (content != null && !content.isEmpty()) {
                        responseParts.add(content);
                    }
                }
            });
        } finally {
            AdkToolRequestContext.clear();
        }

        String text = mergeAdkTextWithSearchFailure(
                String.join("\n", responseParts),
                searchProductsFailureDetail.get(),
                lastSuccessfulSearchTool.get());
        if (hasSearchProductsErrorWithNoSuccess(searchProductsFailureDetail.get(), lastSuccessfulSearchTool.get())) {
            log.warn(
                    "ADK turn: searchProducts failed with no successful retry in this turn (sessionId={}). See prior warn lines for GCP message.",
                    sessionId);
        }

        var responseBuilder = AgentResponse.builder()
                .text(text)
                .conversationId(sessionId);
        if (!lastSearchSuggestions.isEmpty()) {
            responseBuilder.suggestedAnswers(List.copyOf(lastSearchSuggestions));
        }

        Map<String, Object> toolMap = lastSuccessfulSearchTool.get();
        if (toolMap != null && vaisrRetailProductResolver != null) {
            var vaisrResult = toolMapToResult(toolMap);
            VaisrRetailProductResolver.Augmentation aug = vaisrRetailProductResolver.resolve(
                    vaisrResult, message != null ? message : "", context);
            String qt = vaisrResult.queryType();
            responseBuilder
                    .text(aug.text())
                    .refinedQuery(aug.refinedQuery().isEmpty() ? null : aug.refinedQuery())
                    .products(aug.products())
                    .queryType(qt != null && !qt.isEmpty() ? qt : "UNKNOWN")
                    .source(aug.responseSource())
                    .productFilter(aug.productFilter())
                    .productTotalSize(aug.productTotalSize())
                    .productTotalSizeIsApproximate(aug.productTotalSizeIsApproximate())
                    .productNextPageToken(aug.productNextPageToken())
                    .clarifyingQuestion(aug.clarifyingQuestion());
            if (commerceConfig != null && shouldDropStorageSuggestionsAfterSmallResult(aug, commerceConfig)) {
                List<ConversationalCommerceClient.SuggestedAnswer> stripped =
                        ClarifyingFollowUpPolicy.withoutStorageSuggestions(List.copyOf(lastSearchSuggestions));
                responseBuilder.suggestedAnswers(stripped);
            }
        }

        return responseBuilder.build();
    }

    private static final String PRODUCT_SEARCH_FAILURE_USER_MESSAGE =
            "Product search could not reach the Google Retail Conversational Commerce API. "
                    + "Check server logs for the exact error. Typical causes: missing or expired "
                    + "GOOGLE_APPLICATION_CREDENTIALS / Application Default Credentials, incorrect "
                    + "GCP project or Retail placement, VPN or firewall blocking https://retail.googleapis.com, "
                    + "or the Retail / Conversational Commerce API not enabled for your project.";

    /**
     * Package-private for tests: when searchProducts returned error and never succeeded in the same turn,
     * prefer an actionable message over the model apology.
     */
    static boolean hasSearchProductsErrorWithNoSuccess(String failureDetail, Map<String, Object> lastSuccessfulTool) {
        return failureDetail != null && !failureDetail.isBlank() && lastSuccessfulTool == null;
    }

    private static boolean shouldDropStorageSuggestionsAfterSmallResult(
            VaisrRetailProductResolver.Augmentation aug, ConversationalCommerceConfig config) {
        if (aug.products() == null || aug.products().isEmpty()) {
            return false;
        }
        if (aug.clarifyingQuestion() != null && !aug.clarifyingQuestion().isBlank()) {
            return false;
        }
        int count = ClarifyingFollowUpPolicy.effectiveProductCountForClarifying(
                aug.products().size(), aug.productTotalSize());
        return !ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(count, config.productCountThreshold());
    }

    static String mergeAdkTextWithSearchFailure(
            String joinedModelText, String failureDetail, Map<String, Object> lastSuccessfulTool) {
        if (!hasSearchProductsErrorWithNoSuccess(failureDetail, lastSuccessfulTool)) {
            if (joinedModelText == null || joinedModelText.isEmpty()) {
                return "I'm here to help. Could you tell me more about what you're looking for?";
            }
            return joinedModelText;
        }
        String detail = failureDetail != null ? failureDetail.trim() : "";
        if (detail.contains("Conversational Commerce not configured")) {
            return "Product search is not configured on the server (Conversational Commerce client or placement/branch). "
                    + "Check backend startup logs and `conversational-commerce.*` in application.yml.";
        }
        if (detail.contains("Search query was empty")) {
            return "The assistant called product search without a query text. Please try again with what you want to buy "
                    + "(for example \"rice\").";
        }
        StringBuilder sb = new StringBuilder(PRODUCT_SEARCH_FAILURE_USER_MESSAGE);
        if (!detail.isEmpty()) {
            String excerpt = detail.length() > 500 ? detail.substring(0, 500) + "…" : detail;
            sb.append("\n\nFrom the service: ").append(excerpt);
        }
        return sb.toString();
    }

    static ConversationalCommerceClient.ConversationalCommerceResult toolMapToResult(Map<String, Object> resp) {
        List<ConversationalCommerceClient.SuggestedAnswer> sa = new ArrayList<>();
        replaceSuggestedAnswersFromToolMap(resp, sa);
        String refined = Objects.toString(resp.get("refinedQuery"), "").trim();
        if (refined.isEmpty()) {
            refined = Objects.toString(resp.get("submittedQuery"), "").trim();
        }
        return new ConversationalCommerceClient.ConversationalCommerceResult(
                Objects.toString(resp.get("text"), ""),
                Objects.toString(resp.get("conversationId"), ""),
                refined,
                Objects.toString(resp.get("queryType"), ""),
                "agent",
                null,
                sa);
    }

    /**
     * Resolves or creates an ADK session. InMemoryRunner requires sessions to exist before runAsync.
     * If conversationId is provided and the session exists, reuse it. Otherwise create a new session.
     */
    private String resolveOrCreateSession(String conversationId, String visitorId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            Session existing = runner.sessionService()
                    .getSession(APP_NAME, visitorId, conversationId, Optional.empty())
                    .blockingGet();
            if (existing != null) {
                return existing.id();
            }
            // Session doesn't exist, create one with the requested ID for multi-turn
            Session session = runner.sessionService()
                    .createSession(APP_NAME, visitorId, null, conversationId)
                    .blockingGet();
            return session.id();
        }
        Session session = runner.sessionService().createSession(APP_NAME, visitorId, null, null).blockingGet();
        return session.id();
    }

    /**
     * Copies VAISR suggested answers from the latest searchProducts tool result so the API/UI match Approach A.
     */
    static void replaceSuggestedAnswersFromToolMap(
            Map<String, Object> response,
            List<ConversationalCommerceClient.SuggestedAnswer> out) {
        out.clear();
        if (response == null) {
            return;
        }
        Object sa = response.get("suggestedAnswers");
        if (!(sa instanceof List<?> list)) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Object dt = m.get("displayText");
            Object v = m.get("value");
            if (dt != null && v != null) {
                out.add(new ConversationalCommerceClient.SuggestedAnswer(dt.toString(), v.toString()));
            }
        }
    }
}
