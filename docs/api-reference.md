# HTTP API reference

This page describes the **REST API** exposed by the Spring Boot backend. The **authoritative machine-readable spec** is **OpenAPI 3** (generated from controllers and `@Schema` annotations on `ChatRequest` / `ChatResponse`).

---

## Interactive docs (OpenAPI / Swagger)

When the backend is running (default local URL **http://localhost:8080**):

| Resource | URL |
|----------|-----|
| **Swagger UI** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) (root **`/`** may redirect here when the SPA is not served) |
| **OpenAPI JSON** | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |

Use Swagger UI to **try requests**, see **schemas**, and download the spec for code generation.

**Also documented in repo:** [CODE.md](../CODE.md) (condensed tables), [frontend-and-chat-api.md](frontend-and-chat-api.md) (how the React client uses the chat API).

**Source types (keep in sync with API):**

- Backend: `ChatRequest.java`, `ChatResponse.java` (`com.conversationalcommerce.agent.web`)
- Frontend: `frontend/src/api/types.ts`

---

## Endpoints overview

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | **`/api/chat`** | Main conversational commerce chat: text, image, load-more, context for multi-turn |
| `GET` | **`/api/models`** | List **Gemini** model ids (when `GeminiModelsService` is available) |
| `GET` | **`/`** | If `app.serve-frontend=true` and static `index.html` exists → SPA; else **302** to Swagger UI |
| `GET` | **`/favicon.ico`** | **204 No Content** (placeholder) |

All JSON APIs use **`application/json`**.

---

## `POST /api/chat`

**Tag:** Chat (`ChatController`)

**Summary:** Process a user turn using the selected **`mode`**: Approach A (**`convo_commerce`**) or Approach B (**`adk_orchestrator`**).

### Request body (`ChatRequest`)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| **`mode`** | `convo_commerce` \| `adk_orchestrator` | **Yes** | Which orchestrator runs (`OrchestratorService`). |
| **`message`** | string | * | User text. May be empty only if **`imageBase64`** or **`productPageToken`** is set (see validation). |
| **`conversationId`** | string | No | Multi-turn id from GCP / prior response (`conversationId` in **`ChatResponse`**). |
| **`sessionId`** | string | No | Visitor/session correlation (server may generate if omitted in some paths). |
| **`imageBase64`** | string | No | Image for multimodal **conversationalSearch** (raw base64 or data URL; blank trimmed to null). |
| **`maxSuggestedAnswers`** | integer | No | Cap on suggested answers from the pipeline; UI may slice further. |
| **`previousAssistantText`** | string | No | Prior assistant message (for no-products / storage recovery context). |
| **`previousSuggestedAnswers`** | array of `{ displayText, value }` | No | Prior chips (same shape as response `suggestedAnswers`). |
| **`previousRefinedQuery`** | string | No | Last refined search string (e.g. “Any” / no-preference recovery). |
| **`productPageToken`** | string | No | **Load more:** next page token from last **`ChatResponse`**. |
| **`previousProductFilter`** | string | No | **Load more:** Retail filter string from last response (`productFilter`). |
| **`productPageSize`** | integer | No | Page size override; null uses config default. |
| **`productPool`** | array of **product-like objects** | No | Products from the last grid for **in-memory refinement** (`ProductPoolInput` in Java mirrors **`ProductDto`** fields). |
| **`useSemanticReranking`** | boolean | No | When **`productPool`** is sent: allow Vertex semantic reranking (default true if omitted in business logic). |

**Validation:** At least one of **`message`** (non-blank), **`imageBase64`** (non-blank), or **`productPageToken`** (non-blank) must be present (`ChatRequest.isValidInput()`). Invalid body → **400** with RFC 9457 **`ProblemDetail`** (see [Errors](#errors)).

### Response (`ChatResponse`)

| Field | Type | Description |
|-------|------|-------------|
| **`text`** | string | Main assistant reply text. |
| **`conversationId`** | string | Conversation/session id to send on the next turn. |
| **`refinedQuery`** | string? | Search query derived from conversational search (when applicable). |
| **`products`** | array of **ProductDto** | Product grid for this turn. |
| **`source`** | string | `"agent"` (GCP/model) vs `"app"` (server-generated fallback). |
| **`queryType`** | string? | e.g. `SIMPLE_PRODUCT_SEARCH`, `GENERAL_QUESTION`. |
| **`rawResponse`** | string? | Raw conversational GCP JSON (when available; useful for debugging). |
| **`suggestedAnswers`** | array of `{ displayText, value }` | Quick-reply chips; **`value`** is what the client should send as **`message`** when tapped. |
| **`productTotalSize`** | long? | Catalog hit count when known; omitted or negative mapped to null in HTTP layer. |
| **`productTotalSizeIsApproximate`** | boolean? | True when total is estimated from pages. |
| **`productNextPageToken`** | string? | Present when more pages exist (**Load more**). |
| **`productFilter`** | string? | Retail filter used; echo on pagination. |
| **`clarifyingQuestion`** | string? | Follow-up question often shown after the product grid. |

**`ProductDto`** (response) includes at least: `id`, `title`, `description`, `price`, optional `imageUri`, `gtin`, `productId`, `categories`, `brands`, `uri`, `availability`, `sizes`, `materials`, `attributes`, `detailsFetched`. See **`ChatResponse.ProductDto`** in code; **`id`** is the Retail resource **name** when returned by search (used for optional **Product.get** enrichment).

### Example

```http
POST /api/chat HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "mode": "convo_commerce",
  "message": "white rice",
  "conversationId": "",
  "sessionId": "session-123"
}
```

---

## `GET /api/models`

**Tag:** Models (`ModelsController`)

**Summary:** Returns **`List<String>`** of Gemini model resource names (or ids) available for the configured API key / Vertex project.

- **200 OK** — JSON array of strings (possibly empty if none listed).
- **503 Service Unavailable** — **`GeminiModelsService`** bean not present; body is **`[]`**.

Requires **`GOOGLE_API_KEY`** and/or Vertex/ADC setup as described in [CONFIG.md](../CONFIG.md).

---

## Errors

The app uses **`ProblemDetail`** ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)) for consistent JSON errors.

| HTTP | When |
|------|------|
| **400** | Bean validation failure (e.g. **`ChatRequest`** missing `mode`, or neither message, image, nor page token) — `title`: **Validation Error**. |
| **404** | Unknown static resource (`NoResourceFoundException`). |
| **503** | GCP/gRPC unreachable or misconfigured (e.g. invalid credentials, ALPN issues) — often `title`: **Service Configuration Error**. |
| **500** | Unhandled server error — `title`: **Internal Server Error**. |

Exact `detail` strings may include GCP hints (see **`GlobalExceptionHandler`**).

---

## Related narrative docs

| Document | Content |
|----------|---------|
| [user-flow-and-services.md](user-flow-and-services.md) | What runs in the backend per user action |
| [frontend-and-chat-api.md](frontend-and-chat-api.md) | UI behavior and important request fields |
| [product-search-and-retail-apis.md](product-search-and-retail-apis.md) | Conversational search vs Retail product search |
