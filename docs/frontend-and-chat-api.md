# Frontend and chat API

## UI flow (summary)

1. User sends text, picks a **suggested answer**, uses **voice** or **image**, or clicks **Load more** / **Get more suggestions**.
2. **`useChat`** (`frontend/src/hooks/useChat.ts`) updates local message state and calls **`sendChatMessage`** (`frontend/src/api/chatApi.ts`).
3. **`POST /api/chat`** hits the Spring **`ChatController`**, which validates the body and calls **`OrchestratorService.process`**.
4. The JSON response becomes an assistant **message** (text, products, suggested answers, clarifying question, pagination tokens, etc.).

The Vite dev server proxies **`/api`** to the backend (see `frontend/vite.config.ts`). Production may use `VITE_API_URL`.

## Important `ChatRequest` fields for multi-turn

These align with what **`OrchestratorService`** puts in the adapter **context**:

| Field | Purpose |
|-------|---------|
| `mode` | `convo_commerce` \| `adk_orchestrator` |
| `message` | User text (or empty when only image / load-more semantics apply on backend) |
| `conversationId` | GCP / session continuity |
| `sessionId` | Visitor correlation |
| `imageBase64` | Visual search payload |
| `maxSuggestedAnswers` | Cap on suggested chips |
| `previousAssistantText` | Context for follow-ups |
| `previousSuggestedAnswers` | Prior chips |
| `previousRefinedQuery` | Used for no-preference recovery and related logic |
| `productPageToken` | **Load more**: next page |
| `previousProductFilter` | **Load more**: same filter as prior search |
| `productPageSize` | Page size override |

Exact Java types and validation: **`ChatRequest.java`**, **`ChatController.java`**.

## Response shape

**`ChatResponse`** includes `text`, `conversationId`, `refinedQuery`, `products`, `suggestedAnswers`, `clarifyingQuestion`, `rawResponse`, pagination fields (`productNextPageToken`, `productTotalSize`, …), and metadata like `queryType` and `source`.

See **[CODE.md](../CODE.md)** for example JSON and frontend **Message** / **ProductDto** types.

## Voice input and voice output (Google Chrome only)

The **microphone** and **speaker** controls are **disabled** unless the app detects **Google Chrome** (desktop: `Chrome/` + `vendor === 'Google Inc.'`, excluding Edge/Opera; iOS: `CriOS/`). Hovering shows a short message to use Chrome for that feature.

Implementation: **`frontend/src/utils/chromeVoiceSupport.ts`** (`isGoogleChrome`, tooltip strings). **`VoiceInput`**, **`VoiceOutputToggle`**, and **`ChatInterface`** apply this behavior.

## Raw panel

**`RawResponsePanel`** shows **`rawResponse`** (and related history) for debugging conversational GCP payloads—useful when tracing refined queries and suggested answers.
