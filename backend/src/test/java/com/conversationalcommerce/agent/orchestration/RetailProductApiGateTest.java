package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailProductApiGateTest {

    @Test
    void whenDisabled_alwaysAllowsCatalogAndNeverTracksSession() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(false);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("c1", "s1");
        assertThat(gate.mayQueryRetailCatalogSearch("a", "f")).isTrue();
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.noteRetailProductListingCommitted("a", "f");
        gate.endChatTurn();

        gate.beginChatTurn("c1", "s1");
        assertThat(gate.mayQueryRetailCatalogSearch("a", "f")).isTrue();
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.endChatTurn();
    }

    @Test
    void catalogBlocksIdenticalQueryFilter_repeatNotBrand() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("conv-a", "sess-1");
        assertThat(gate.mayQueryRetailCatalogSearch("rice", null)).isTrue();
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.noteRetailProductListingCommitted("rice", "");
        assertThat(gate.mayQueryRetailSearchApis()).isFalse();
        gate.endChatTurn();

        gate.beginChatTurn("conv-a", "sess-1");
        assertThat(gate.mayQueryRetailCatalogSearch("rice", null)).isFalse();
        assertThat(gate.mayQueryRetailCatalogSearch("uncle bens", null)).isTrue();
        assertThat(gate.mayQueryRetailSearchApis()).isFalse();
        gate.endChatTurn();
    }

    @Test
    void catalogAllowsDifferentFilter() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("conv-b", "sess-2");
        gate.noteRetailProductListingCommitted("rice", "attributes.stockType: ANY(\"S\")");
        gate.endChatTurn();

        gate.beginChatTurn("conv-b", "sess-2");
        assertThat(gate.mayQueryRetailCatalogSearch("rice", "attributes.stockType: ANY(\"D\")")).isTrue();
        gate.endChatTurn();
    }

    @Test
    void turnWithoutListingDoesNotFingerprint() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("conv-c", "sess-3");
        gate.endChatTurn();

        gate.beginChatTurn("conv-c", "sess-3");
        assertThat(gate.mayQueryRetailCatalogSearch("rice", null)).isTrue();
        gate.endChatTurn();
    }

    @Test
    void prefersConversationIdOverSessionId() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("same", "other-session");
        gate.noteRetailProductListingCommitted("r", "");
        gate.endChatTurn();

        gate.beginChatTurn("same", "different-session");
        assertThat(gate.mayQueryRetailCatalogSearch("r", null)).isFalse();
        gate.endChatTurn();
    }
}
