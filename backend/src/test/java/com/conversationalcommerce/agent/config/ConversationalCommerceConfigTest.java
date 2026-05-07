package com.conversationalcommerce.agent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceConfigTest {

    @Test
    void productSearchPageSize_defaultsTo20() {
        var config = new ConversationalCommerceConfig();
        assertThat(config.productSearchPageSize()).isEqualTo(20);
    }

    @Test
    void productSearchPageSize_usesConfiguredValue() {
        var config = new ConversationalCommerceConfig();
        config.setProductSearchPageSize(50);
        assertThat(config.productSearchPageSize()).isEqualTo(50);
    }

    @Test
    void productSearchPageSize_ignoresInvalidValues() {
        var config = new ConversationalCommerceConfig();
        config.setProductSearchPageSize(0);
        assertThat(config.productSearchPageSize()).isEqualTo(20);
        config.setProductSearchPageSize(-1);
        assertThat(config.productSearchPageSize()).isEqualTo(20);
    }
}
