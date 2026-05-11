package com.conversationalcommerce.agent.orchestration;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps ADK session id (returned as conversationId to the chat client) to the GCP VAISR
 * conversationalSearch conversation id for multi-turn tool calls.
 */
@Component
public class AdkVaisrSessionBridge {

    private final ConcurrentHashMap<String, String> adkSessionToGcpConversationId = new ConcurrentHashMap<>();

    public String getGcpConversationId(String adkSessionId) {
        if (adkSessionId == null || adkSessionId.isBlank()) {
            return null;
        }
        return adkSessionToGcpConversationId.get(adkSessionId);
    }

    public void setGcpConversationId(String adkSessionId, String gcpConversationId) {
        if (adkSessionId == null || adkSessionId.isBlank() || gcpConversationId == null || gcpConversationId.isBlank()) {
            return;
        }
        adkSessionToGcpConversationId.put(adkSessionId, gcpConversationId);
    }
}
