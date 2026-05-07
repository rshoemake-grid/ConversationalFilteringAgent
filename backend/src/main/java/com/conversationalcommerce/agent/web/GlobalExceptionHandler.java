package com.conversationalcommerce.agent.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.google.api.gax.rpc.UnavailableException;
import io.grpc.StatusRuntimeException;

/**
 * Global exception handler for consistent API error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnavailableException.class)
    public ResponseEntity<ProblemDetail> handleGcpUnavailable(UnavailableException ex) {
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof StatusRuntimeException)) {
            cause = cause.getCause();
        }
        return cause instanceof StatusRuntimeException sre
                ? handleGcpStatusRuntime(sre)
                : handleGcpStatusRuntime(ex);
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleGcpError(StatusRuntimeException ex) {
        return handleGcpStatusRuntime(ex);
    }

    private ResponseEntity<ProblemDetail> handleGcpStatusRuntime(Throwable ex) {
        if (!(ex instanceof StatusRuntimeException sre)) {
            log.warn("GCP API error: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                            "GCP service unavailable: " + ex.getMessage()));
        }
        log.warn("GCP API error (Conversational Commerce may be enabled without proper config): {}", sre.getMessage());
        String gcpError = sre.getStatus().getCode().name() + ": " + sre.getMessage();
        String detail = "Conversational Commerce requires valid GCP credentials and a configured Retail catalog. "
                + "GCP error: " + gcpError + ".";
        if (gcpError.contains("ALPN") || gcpError.contains("Failed ALPN")) {
            detail += " If ALPN negotiation fails, try: disable VPN, or run with -Djdk.tls.client.protocols=TLSv1.2,TLSv1.3";
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle("Service Configuration Error");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException ex) {
        log.debug("No resource found: {}", ex.getResourcePath());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
        problem.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Error");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        // Unwrap cause chain for GCP/grpc errors
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof UnavailableException) {
                return handleGcpUnavailable((UnavailableException) cause);
            }
            if (cause instanceof StatusRuntimeException) {
                return handleGcpError((StatusRuntimeException) cause);
            }
            cause = cause.getCause();
        }
        String detail = resolveDetailMessage(ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                detail
        );
        problem.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private String resolveDetailMessage(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
            t = t.getCause();
        }
        return ex.getClass().getSimpleName() + ": An unexpected error occurred. Please try again.";
    }
}
