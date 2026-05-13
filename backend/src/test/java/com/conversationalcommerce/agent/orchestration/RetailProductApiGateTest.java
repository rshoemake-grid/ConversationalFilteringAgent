package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailProductApiGateTest {

    @Test
    void whenDisabled_alwaysAllowsAndNeverSeals() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(false);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("c1", "s1");
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.noteRetailProductListingCommitted();
        gate.endChatTurn();

        gate.beginChatTurn("c1", "s1");
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.endChatTurn();
    }

    @Test
    void whenEnabled_secondTurnBlocksAfterFirstListingCommitted() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("conv-a", "sess-1");
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.noteRetailProductListingCommitted();
        gate.endChatTurn();

        gate.beginChatTurn("conv-a", "sess-1");
        assertThat(gate.mayQueryRetailSearchApis()).isFalse();
        gate.endChatTurn();
    }

    @Test
    void whenEnabled_turnWithoutListingDoesNotSeal() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("conv-b", "sess-2");
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.endChatTurn();

        gate.beginChatTurn("conv-b", "sess-2");
        assertThat(gate.mayQueryRetailSearchApis()).isTrue();
        gate.noteRetailProductListingCommitted();
        gate.endChatTurn();

        gate.beginChatTurn("conv-b", "sess-2");
        assertThat(gate.mayQueryRetailSearchApis()).isFalse();
        gate.endChatTurn();
    }

    @Test
    void prefersConversationIdOverSessionId() {
        var config = new ConversationalCommerceConfig();
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);

        gate.beginChatTurn("same", "other-session");
        gate.noteRetailProductListingCommitted();
        gate.endChatTurn();

        gate.beginChatTurn("same", "different-session");
        assertThat(gate.mayQueryRetailSearchApis()).isFalse();
        gate.endChatTurn();
    }
}
