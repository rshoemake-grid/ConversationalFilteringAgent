package com.conversationalcommerce.agent.web;

import com.conversationalcommerce.agent.gemini.GeminiModelsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Tag(name = "Models", description = "Gemini model listing")
public class ModelsController {

    private final Optional<GeminiModelsService> modelsService;

    public ModelsController(Optional<GeminiModelsService> modelsService) {
        this.modelsService = modelsService;
    }

    @Operation(summary = "List Gemini models", description = "Returns available Gemini models that support generateContent. Requires GOOGLE_API_KEY or app.gemini.api-key.")
    @GetMapping("/models")
    public ResponseEntity<List<String>> listModels() {
        return modelsService
                .map(service -> ResponseEntity.ok(service.listModels()))
                .orElse(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of()));
    }
}
