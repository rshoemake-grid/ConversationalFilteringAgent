package com.conversationalcommerce.agent.orchestration;

import java.util.Map;

/**
 * Request-scoped context for static ADK tools ({@link com.conversationalcommerce.agent.tool.ConversationalCommerceTool}).
 * Set for the duration of {@code AdkOrchestrator.process}; cleared in a {@code finally} block.
 */
public final class AdkToolRequestContext {

    /** Inheritable so worker threads spawned during ADK tool runs can still see session context. */
    private static final InheritableThreadLocal<Holder> CURRENT = new InheritableThreadLocal<>();

    private AdkToolRequestContext() {}

    public record Holder(String adkSessionId, String visitorId, Map<String, Object> context, AdkVaisrSessionBridge bridge) {}

    public static void set(String adkSessionId, String visitorId, Map<String, Object> context, AdkVaisrSessionBridge bridge) {
        CURRENT.set(new Holder(adkSessionId, visitorId, context, bridge));
    }

    public static Holder current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
