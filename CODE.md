# Code Documentation

Technical documentation for the Conversational Filtering Agent codebase.

**Narrative guides** (how the system works end-to-end, especially **user flows and which services are called**): see **[docs/user-flow-and-services.md](docs/user-flow-and-services.md)** and the index **[docs/README.md](docs/README.md)**.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Backend Structure](#backend-structure)
- [Frontend Structure](#frontend-structure)
- [API Reference](#api-reference)
- [Data Models](#data-models)
- [Key Flows](#key-flows)
- [Configuration](#configuration)

---

## Architecture Overview

The application is a full-stack chat interface for Google's GCP Conversational Commerce (Retail API), with two orchestration modes:

| Mode | Description |
|------|-------------|
| **convo_commerce** | **`ConversationalCommerceAdapter`**: Retail **conversationalSearch** plus **Retail Search** for products; optional enrichment and Vertex ranking. Default wiring does not use ADK/Gemini as the orchestrator. |
| **adk_orchestrator** | **ADK `LlmAgent`** (Gemini) orchestrates; **ConversationalCommerceTool** calls **conversationalSearch**; **VaisrRetailProductResolver** loads products via **Retail Search**. Other tools may be configured on the agent. |

### Technology Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.2, Maven |
| Frontend | React 19, Vite 7, TypeScript |
| GCP | Vertex AI Search for commerce, Gemini (ADK) |
| API | REST, JSON, OpenAPI 3 (Swagger) |

---

## Backend Structure

```
backend/src/main/java/com/conversationalcommerce/agent/
├── Application.java                 # Spring Boot entry point
├── agent/                           # Agent abstractions and implementations
│   ├── ConversationalAgent.java     # Interface: sendMessage, getAgentId
│   ├── ConversationalCommerceClient.java  # Interface for GCP Conversational Commerce API
│   ├── ConversationalCommerceAdapter.java # Implements ConversationalAgent via GCP
│   ├── RetailConversationalSearchClientRest.java  # REST impl of ConversationalCommerceClient
│   ├── RetailConversationalSearchClient.java      # gRPC impl (alternate transport)
│   ├── RetailSearchClient.java      # Interface for product search
│   ├── RetailSearchClientImpl.java  # gRPC product search
│   ├── RetailSearchClientRest.java  # REST product search
│   └── AgentResponse.java           # Response DTO
├── config/                          # Spring configuration
│   ├── ConversationalCommerceConfig.java
│   ├── GcpCredentialsProvider.java
│   ├── AdkConfig.java
│   ├── WebConfig.java               # CORS, static SPA serving
│   └── ...
├── gemini/                          # Gemini/GeminiModelsService integration
├── orchestration/                   # Chat orchestrators
│   ├── ChatOrchestrator.java        # Interface
│   ├── OrchestratorService.java     # Routes by mode to ConvoCommerce or ADK
│   ├── ConvoCommerceOrchestrator.java
│   ├── AdkOrchestrator.java
│   └── ...
├── tool/                            # ADK tools (for adk_orchestrator mode)
│   ├── ConversationalCommerceTool.java
│   └── LoyaltyRecommendationTool.java
└── web/                             # REST controllers
    ├── ChatController.java          # POST /api/chat
    ├── ModelsController.java        # GET /api/models
    ├── RootController.java          # /, favicon
    ├── ChatRequest.java             # Request DTO
    ├── ChatResponse.java            # Response DTO
    └── GlobalExceptionHandler.java
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `OrchestratorService` | Entry point; routes chat by `mode` to `ConvoCommerceOrchestrator` or `AdkOrchestrator`. Passes `maxSuggestedAnswers` in context. |
| `ConversationalCommerceAdapter` | Wraps GCP Conversational Commerce API. Builds request, calls client, runs product search on refined query, returns `AgentResponse`. |
| `RetailConversationalSearchClientRest` | REST transport for GCP `conversationalSearch` (bypasses gRPC/ALPN). Parses suggested answers from JSON response. |
| `ChatController` | Accepts `ChatRequest`, returns `ChatResponse`. Validates mode + message/image. |
| `WebConfig` | CORS for `/api/**`, SPA static serving when `app.serve-frontend=true`. |

---

## Frontend Structure

```
frontend/src/
├── api/
│   ├── chatApi.ts       # sendChatMessage()
│   └── types.ts        # Message, ChatRequest, ChatResponse, ProductDto, SuggestedAnswer
├── components/
│   ├── ChatInterface.tsx        # Main UI, wires useChat
│   ├── MessageList.tsx          # Renders messages, suggested answers, products
│   ├── ProductCard.tsx         # Product display + hover popover
│   ├── ModeSelector.tsx        # convo_commerce | adk_orchestrator
│   ├── MaxSuggestedAnswersControl.tsx  # Real-time max suggestions (1–50)
│   ├── VoiceInput.tsx          # Web Speech API (mic)
│   ├── VoiceOutputToggle.tsx   # TTS on/off
│   ├── ImageInput.tsx          # Image upload for visual search
│   ├── RawResponsePanel.tsx    # Sidebar raw JSON
│   └── ErrorBoundary.tsx
├── hooks/
│   ├── useChat.ts              # Main chat state and handlers
│   └── useVoiceOutput.ts       # speak(), stop(), isSpeaking
├── App.tsx
└── main.tsx
```

### Key Hooks & Components

| Hook/Component | Purpose |
|----------------|---------|
| `useChat` | State: `messages`, `mode`, `maxSuggestedAnswers`, `loading`, etc. Handlers: `handleSend`, `handleSuggestedAnswer`, `handleGetMoreSuggestions`, `handleRetry`. Manages `conversationId`, `failedSuggestedValuesRef`, sends requests to `sendChatMessage`. |
| `chatApi.sendChatMessage` | POST to `/api/chat` with `ChatRequest`. Returns `ChatResponse`. Uses `VITE_API_URL ?? '/api'`. |
| `MessageList` | Renders messages. Slices suggested answers by `maxSuggestedAnswers`. "Get more suggestions" button on last assistant message with suggestions. |
| `MaxSuggestedAnswersControl` | Number input 1–50; changes apply in real time to displayed suggestions. |
| `ProductCard` | Product display; hover shows popover with full ProductDto. |

---

## API Reference

### POST /api/chat

Send a chat message. Supports text, image (base64), and multimodal.

**Request body (ChatRequest):**

```json
{
  "mode": "convo_commerce" | "adk_orchestrator",
  "message": "string",
  "conversationId": "string (optional, for multi-turn)",
  "sessionId": "string (optional)",
  "imageBase64": "string (optional)",
  "maxSuggestedAnswers": 8
}
```

**Response (ChatResponse):**

```json
{
  "text": "string",
  "conversationId": "string",
  "refinedQuery": "string (optional)",
  "products": [ { "id", "title", "description", "price", "imageUri", "gtin", ... } ],
  "source": "agent" | "app",
  "queryType": "string (optional)",
  "rawResponse": "string (optional, JSON)",
  "suggestedAnswers": [ { "displayText": "string", "value": "string" } ]
}
```

**Validation:** Either `message` (non-blank) or `imageBase64` must be provided.

### GET /api/models

Returns available Gemini models. Requires `GOOGLE_API_KEY`.

**Endpoints:**

| Path | Purpose |
|------|---------|
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | OpenAPI JSON |
| `/` | SPA `index.html` when `app.serve-frontend=true`, else redirect to Swagger |

---

## Data Models

### Message (frontend)

| Field | Type | Description |
|-------|------|-------------|
| id | string | UUID |
| role | 'user' \| 'assistant' | Message source |
| content | string | Display text |
| products | ProductDto[] | Optional products |
| suggestedAnswers | SuggestedAnswer[] | Quick-reply options |
| isError | boolean | Error message flag |
| imageUri | string | Data URL for display |
| imageBase64 | string | Raw base64 for retry |
| source | 'agent' \| 'app' | Response source |
| queryType | string | GCP classification |

### ProductDto

| Field | Type | Description |
|-------|------|-------------|
| id | string | GCP Retail **resource name** (`Product.name`), e.g. `projects/…/branches/…/products/…`. Backend **Product.Get** enrichment uses this (must contain `/products/`). |
| title, description, price | string | Core fields |
| imageUri | string | Product image URL |
| gtin | string | UPC/GTIN |
| productId | string | Short catalog id (not used for Product.Get in this app) |
| categories, brands | string[] | Taxonomies |
| uri | string | Product link |
| availability, sizes, materials | string/string[] | Attributes |
| attributes | Record | Custom key-value |

### SuggestedAnswer

| Field | Type | Description |
|-------|------|-------------|
| displayText | string | Shown in UI |
| value | string | Sent to API when selected |

When the agent asks a follow-up question without suggested answers (e.g. "Do you have a preference for long-grain, medium-grain, or short-grain rice?"), the app adds an "Any" option so the user can indicate no preference without typing.

---

## Key Flows

For a **deeper explanation** of the GCP **conversational search** + **Retail Search** steps, see **[docs/product-search-and-retail-apis.md](docs/product-search-and-retail-apis.md)**.

### Chat Message Flow

1. User types or selects suggested answer.
2. `useChat.handleSend` or `handleSuggestedAnswer` adds user message, calls `sendChatMessage`.
3. Backend `ChatController` → `OrchestratorService` → `ConvoCommerceOrchestrator` or `AdkOrchestrator`.
4. Orchestrator calls `ConversationalCommerceAdapter.sendMessage` (or tools for ADK).
5. `RetailConversationalSearchClientRest` calls GCP `conversationalSearch`, parses response.
6. If `refinedQuery` present, `RetailSearchClient` runs product search.
7. Response built as `AgentResponse` → `ChatResponse`.
8. Frontend appends assistant message, filters failed suggested answers, slices by `maxSuggestedAnswers`.
9. **No-preference recovery**: When the user says "Any", "no", or "no preference" and the API returns empty `refinedQuery`, the backend uses `previousRefinedQuery` from context to run a product search. Handles both RETAIL_IRRELEVANT and SIMPLE_PRODUCT_SEARCH regression (e.g. API asks stock type again after the user selected "Any" for rice refinement).

### Suggested Answers

- GCP returns suggested answers in response JSON.
- Backend parses and maps brand codes to display text (e.g. NIKE → Nike).
- **"Any" for follow-up questions**: When the agent asks a follow-up question (text contains "?") but the API provides no suggested answers, we add an "Any" option so the user can indicate no preference with one click. Selecting "Any" triggers the no-preference recovery flow (search using previous refined query).
- Frontend tracks `failedSuggestedValuesRef`: suggestions that returned same assistant text (no products) are hidden.
- `maxSuggestedAnswers` caps displayed count; applies in real time.
- "Get more suggestions" resends last user message and clears failed set.

---

## Configuration

See [CONFIG.md](CONFIG.md) for credentials and environment variables.

**Backend (`application.yml`):**

| Key | Default | Description |
|-----|---------|-------------|
| `app.serve-frontend` | false | Serve SPA from `/` |
| `app.cors.allowed-origins` | * | CORS origins |
| `app.gemini.api-key` | env | Gemini API key |
| `app.gemini.referer` | localhost:5173 | For restricted API keys |
| `conversational-commerce.transport` | rest | rest \| grpc |
| `conversational-commerce.conversational-filtering-mode` | ENABLED | ENABLED \| DISABLED |

**Frontend:**

| Env | Description |
|-----|-------------|
| `VITE_API_URL` | Backend API base (default: `/api`) |
