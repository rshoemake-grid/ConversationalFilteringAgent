package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PoC policy: after {@link com.conversationalcommerce.agent.agent.InitialCatalogAggregator} has run a product
 * listing phase for a conversation/session, block further Retail Search used for catalog listing and for
 * brand display resolution (same Search API). Does not block Product.get enrichment or conversational search.
 * <p>
 * Does not affect Retail conversational search (separate client). Scoped to the HTTP request thread via
 * {@link ThreadLocal}; parallel catalog fetches decide allowance on the submitting thread before work is queued.
 */
@Component
public class RetailProductApiGate {

    private final ConversationalCommerceConfig config;
    /** Conversation or session keys that have completed at least one catalog listing phase. */
    private final Set<String> sealedKeys = ConcurrentHashMap.newKeySet();

    private static final ThreadLocal<TurnCtx> CTX = new ThreadLocal<>();

    private static final class TurnCtx {
        final String key;
        final boolean blockedAtStart;
        boolean listingCommitted;

        TurnCtx(String key, boolean blockedAtStart) {
            this.key = key;
            this.blockedAtStart = blockedAtStart;
        }
    }

    public RetailProductApiGate(ConversationalCommerceConfig config) {
        this.config = config;
    }

    public void beginChatTurn(String conversationId, String sessionId) {
        if (!config.retailSingleShotPerConversation()) {
            return;
        }
        String key = resolveKey(conversationId, sessionId);
        boolean blocked = sealedKeys.contains(key);
        CTX.set(new TurnCtx(key, blocked));
    }

    public void endChatTurn() {
        try {
            TurnCtx ctx = CTX.get();
            if (ctx != null && config.retailSingleShotPerConversation() && ctx.key != null && ctx.listingCommitted) {
                sealedKeys.add(ctx.key);
            }
        } finally {
            CTX.remove();
        }
    }

    /**
     * Retail Search on the catalog branch (initial listing and brand lookup). Does not apply to Product.get.
     */
    public boolean mayQueryRetailSearchApis() {
        if (!config.retailSingleShotPerConversation()) {
            return true;
        }
        TurnCtx ctx = CTX.get();
        if (ctx == null) {
            return true;
        }
        return !ctx.blockedAtStart;
    }

    /**
     * Called on the request thread when {@link com.conversationalcommerce.agent.agent.InitialCatalogAggregator}
     * is about to invoke Retail Search for catalog listing (so worker threads need not read this gate).
     */
    public void noteRetailProductListingCommitted() {
        if (!config.retailSingleShotPerConversation()) {
            return;
        }
        TurnCtx ctx = CTX.get();
        if (ctx != null) {
            ctx.listingCommitted = true;
        }
    }

    static String resolveKey(String conversationId, String sessionId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return "c:" + conversationId.trim();
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return "s:" + sessionId.trim();
        }
        return "_anon";
    }
}
