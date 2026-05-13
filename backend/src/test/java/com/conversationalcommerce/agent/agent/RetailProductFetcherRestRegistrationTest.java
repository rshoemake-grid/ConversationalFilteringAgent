package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.GcpCredentialsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Product.Get enrichment must work when Retail Search uses gRPC ({@code transport=grpc});
 * otherwise {@link ProductEnrichmentService} never receives a {@link ProductFetcher} and attributes stay null.
 */
class RetailProductFetcherRestRegistrationTest {

    @Test
    void retailProductFetcherRest_isRegistered_whenTransportIsGrpc() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestBeans.class)
                .withPropertyValues("conversational-commerce.transport=grpc")
                .run(context -> assertThat(context).hasSingleBean(RetailProductFetcherRest.class));
    }

    @Configuration
    @Import(RetailProductFetcherRest.class)
    static class TestBeans {
        @Bean
        GcpCredentialsProvider gcpCredentialsProvider() {
            return new GcpCredentialsProvider("", "");
        }
    }
}
