package com.conversationalcommerce.agent.config;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Approach A specialist: ADK agent for general questions (store hours, policies).
 */
@Configuration
public class GeneralQuestionAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(GeneralQuestionAgentConfig.class);

    private static final String APP_NAME = "general_question_specialist";

    @Value("${app.gemini.model:gemini-2.5-flash}")
    private String model;

    private final Environment environment;
    private final GeminiClientFactory clientFactory;

    public GeneralQuestionAgentConfig(Environment environment, GeminiClientFactory clientFactory) {
        this.environment = environment;
        this.clientFactory = clientFactory;
    }

    @Bean
    public LlmAgent generalQuestionAgent() {
        var builder = LlmAgent.builder();
        if (GeminiRuntimeAuth.canUseGemini(environment)) {
            try {
                Client client = clientFactory.buildClientForGemini(environment);
                var geminiModel = Gemini.builder()
                        .modelName(model)
                        .apiClient(client)
                        .build();
                builder.model(geminiModel);
            } catch (Exception e) {
                log.warn("Could not build Gemini client (API key or Vertex/ADC): {}", e.getMessage());
                builder.model(model);
            }
        } else {
            builder.model(model);
        }

        return builder
                .name("general_question_specialist")
                .instruction("""
                    You are a helpful store assistant for general questions. Answer concisely.
                    Store hours: 9am-9pm Mon-Sat, 10am-6pm Sun.
                    Return policy: 30 days with receipt. Free shipping on orders over $50.
                    For product-specific questions, suggest the user try the product search.
                    """)
                .description("Handles store hours, policies, and general non-product questions")
                .build();
    }

    @Bean
    public GeneralQuestionSpecialistRunner generalQuestionSpecialistRunner(LlmAgent generalQuestionAgent) {
        return new GeneralQuestionSpecialistRunner(generalQuestionAgent);
    }

    public static class GeneralQuestionSpecialistRunner {
        private final InMemoryRunner runner;

        public GeneralQuestionSpecialistRunner(LlmAgent agent) {
            this.runner = new InMemoryRunner(agent, APP_NAME);
        }

        public AgentResponse run(String message, String userId) {
            Session session = runner.sessionService().createSession(APP_NAME, userId).blockingGet();
            Content userMessage = Content.fromParts(Part.fromText(message));

            List<String> parts = new ArrayList<>();
            Flowable<Event> events = runner.runAsync(userId, session.id(), userMessage);
            events.blockingForEach(e -> {
                if (e.finalResponse()) {
                    String c = e.stringifyContent();
                    if (c != null && !c.isEmpty()) parts.add(c);
                }
            });

            String text = String.join("\n", parts);
            if (text.isEmpty()) text = "I can help with general questions. Our store hours are 9am-9pm.";
            return AgentResponse.builder().text(text).build();
        }
    }
}
