package com.conversationalcommerce.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.security.Security;

@SpringBootApplication
@EnableConfigurationProperties
public class Application {

    public static void main(String[] args) {
        // Install Conscrypt as TLS/ALPN provider before any gRPC client is created.
        // Helps with "Failed ALPN negotiation" when behind VPN or corporate proxy.
        try {
            Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1);
        } catch (Throwable t) {
            // Conscrypt not available on this platform; JVM default will be used
        }
        SpringApplication.run(Application.class, args);
    }
}
