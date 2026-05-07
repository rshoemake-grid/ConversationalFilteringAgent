package com.conversationalcommerce.agent.web;

import java.util.UUID;

import com.conversationalcommerce.agent.orchestration.OrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Chat", description = "Conversational commerce chat API")
public class ChatController {

    private final OrchestratorService orchestratorService;

    public ChatController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @Operation(summary = "Send a chat message", description = "Processes a message using the selected orchestration mode. Supports text, voice (as text), and image (base64) inputs.")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        var response = orchestratorService.process(
                request.mode(),
                request.message(),
                request.conversationId(),
                request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString(),
                request.imageBase64(),
                request.maxSuggestedAnswers(),
                request.previousAssistantText(),
                request.previousSuggestedAnswers(),
                request.previousRefinedQuery(),
                request.productPageToken(),
                request.previousProductFilter(),
                request.productPageSize()
        );
        return ResponseEntity.ok(ChatResponse.from(response));
    }
}
