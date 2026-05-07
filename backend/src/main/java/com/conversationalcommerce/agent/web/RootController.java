package com.conversationalcommerce.agent.web;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles root and favicon requests. When app.serve-frontend=true (e.g. in Docker),
 * serves the SPA index.html; otherwise redirects to Swagger UI.
 */
@RestController
public class RootController {

    @Value("${app.serve-frontend:false}")
    private boolean serveFrontend;

    @GetMapping("/")
    public ResponseEntity<?> root() {
        if (serveFrontend) {
            Resource index = new ClassPathResource("static/index.html");
            if (index.exists()) {
                return ResponseEntity.ok().body(index);
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/swagger-ui/index.html")).build();
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
