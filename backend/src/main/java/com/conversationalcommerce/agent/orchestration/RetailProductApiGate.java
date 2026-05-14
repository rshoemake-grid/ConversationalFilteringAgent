package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PoC policy: limit redundant Retail Search volume per conversation/session.
 * <ul>
 *   <li><b>Catalog listing</b> ({@link com.conversationalcommerce.agent.agent.InitialCatalogAggregator}): allow a new
 *       search when {@code query} or {@code filter} differs from the last committed listing (so follow-ups like
 *       "uncle bens" after "rice" still hit Retail).</li>
 *   <li><b>Brand display</b> resolution: after any catalog listing in the session, skip further search-backed brand
 *       lookups (same as historical single-shot behavior).</li>
 * </ul>
 * Does not block Product.get or conversational search. Uses {@link ThreadLocal} for the current chat turn key.
 */
@Component
public class RetailProductApiGate {

    private final ConversationalCommerceConfig config;

    /** Sessions that have completed at least one catalog listing (brand lookup stays off). */
    private final Set<String> sessionHasCatalogListing = ConcurrentHashMap.newKeySet();

    /** Last catalog listing fingerprint per session (normalized query + filter). */
    private final ConcurrentHashMap<String, CatalogFingerprint> lastCatalogFingerprint = new ConcurrentHashMap<>();

    private static final ThreadLocal<TurnCtx> CTX = new ThreadLocal<>();

    private static final class TurnCtx {
        final String key;

        TurnCtx(String key) {
            this.key = key;
        }
    }

    private record CatalogFingerprint(String queryNorm, String filterNorm) {
        boolean matches(String q, String f) {
            return queryNorm.equals(q) && filterNorm.equals(f);
        }
    }

    public RetailProductApiGate(ConversationalCommerceConfig config) {
        this.config = config;
    }

    public void beginChatTurn(String conversationId, String sessionId) {
        if (!config.retailSingleShotPerConversation()) {
            CTX.remove();
            return;
        }
        CTX.set(new TurnCtx(resolveKey(conversationId, sessionId)));
    }

    public void endChatTurn() {
        CTX.remove();
    }

    /**
     * Retail Search for <strong>brand display</strong> and other non-catalog helpers. After the first catalog
     * listing in a session, returns false (historical PoC behavior).
     */
    public boolean mayQueryRetailSearchApis() {
        if (!config.retailSingleShotPerConversation()) {
            return true;
        }
        TurnCtx ctx = CTX.get();
        if (ctx == null || ctx.key == null) {
            return true;
        }
        return !sessionHasCatalogListing.contains(ctx.key);
    }

    /**
     * Retail Search for {@link com.conversationalcommerce.agent.agent.InitialCatalogAggregator} catalog listing.
     * When single-shot is enabled, blocks only repeats of the same normalized query+filter pair.
     */
    public boolean mayQueryRetailCatalogSearch(String query, String filter) {
        if (!config.retailSingleShotPerConversation()) {
            return true;
        }
        TurnCtx ctx = CTX.get();
        if (ctx == null || ctx.key == null) {
            return true;
        }
        CatalogFingerprint prev = lastCatalogFingerprint.get(ctx.key);
        if (prev == null) {
            return true;
        }
        String q = normalizeQuerySlot(query);
        String f = normalizeQuerySlot(filter);
        return !prev.matches(q, f);
    }

    /**
     * Record a catalog listing for the current turn's session. Called after {@link #mayQueryRetailCatalogSearch}
     * allowed the request (caller may still get an empty result; we still dedupe that fingerprint).
     */
    public void noteRetailProductListingCommitted(String query, String filter) {
        if (!config.retailSingleShotPerConversation()) {
            return;
        }
        TurnCtx ctx = CTX.get();
        if (ctx == null || ctx.key == null) {
            return;
        }
        String k = ctx.key;
        lastCatalogFingerprint.put(k, new CatalogFingerprint(normalizeQuerySlot(query), normalizeQuerySlot(filter)));
        sessionHasCatalogListing.add(k);
    }

    private static String normalizeQuerySlot(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
