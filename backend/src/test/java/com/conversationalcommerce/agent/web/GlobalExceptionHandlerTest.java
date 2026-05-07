package com.conversationalcommerce.agent.web;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGcpError_includesGcpErrorInDetail() {
        StatusRuntimeException ex = new StatusRuntimeException(
                Status.UNAUTHENTICATED.withDescription("Invalid credentials"));

        ResponseEntity<ProblemDetail> response = handler.handleGcpError(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Service Configuration Error");
        assertThat(response.getBody().getDetail())
                .contains("UNAUTHENTICATED")
                .contains("Invalid credentials");
    }

    @Test
    void handleGcpError_includesAlpnHintWhenAlpnError() {
        StatusRuntimeException ex = new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("Failed ALPN negotiation: Unable to find compatible protocol"));

        ResponseEntity<ProblemDetail> response = handler.handleGcpError(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail())
                .contains("ALPN")
                .contains("jdk.tls.client.protocols")
                .contains("TLSv1.2");
    }

    @Test
    void handleNotFound_returns404WithProblemDetail() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "favicon.ico");

        ResponseEntity<ProblemDetail> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Not Found");
        assertThat(response.getBody().getDetail()).isEqualTo("Resource not found");
    }
}
