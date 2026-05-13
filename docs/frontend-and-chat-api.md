# Frontend and chat API

For **HTTP** endpoint reference (**`ChatRequest` / `ChatResponse`** fields, Swagger): **[api-reference.md](api-reference.md)**.

For **end-to-end user journeys** and **which backend/GCP services run** on each action, see **[user-flow-and-services.md](user-flow-and-services.md)**.

## UI flow (summary)

1. User sends text, picks a **suggested answer**, uses **voice** or **image**, or uses **Get more suggestions** where exposed.
2. **`useChat`** (`frontend/src/hooks/useChat.ts`) updates local message state and calls **`sendChatMessage`** (`frontend/src/api/chatApi.ts`).
3. **`POST /api/chat`** hits the Spring **`ChatController`**, which validates the body and calls **`OrchestratorService.process`**.
4. The JSON response becomes an assistant **message** (text, products, suggested answers, clarifying question, pagination tokens, etc.).

The Vite dev server proxies **`/api`** to the backend (see `frontend/vite.config.ts`). Production may use `VITE_API_URL`.

## Important `ChatRequest` fields for multi-turn

These align with what **`OrchestratorService`** puts in the adapter **context**:

| Field | Purpose |
|-------|---------|
| `mode` | `convo_commerce` \| `adk_orchestrator` |
| `message` | User text (or empty when only **`imageBase64`** or legacy **`productPageToken`** satisfies validation) |
| `conversationId` | GCP / session continuity |
| `sessionId` | Visitor correlation |
| `imageBase64` | Visual search payload |
| `maxSuggestedAnswers` | Cap on suggested chips |
| `previousAssistantText` | Context for follow-ups |
| `previousSuggestedAnswers` | Prior chips |
| `previousRefinedQuery` | Used for no-preference recovery and related logic |
| `productPageToken` | **Deprecated;** if sent, backend does **not** call Retail—see [product-search-and-retail-apis.md](product-search-and-retail-apis.md) |
| `previousProductFilter` | Prior Retail filter (context / session merge) |
| `productPageSize` | **Server:** heuristics (totals, suggested-answer ingest rules). **Client:** initial visible product count and **Show more** step when there is no **`productNextPageToken`**. |
| `productPool` | **In-memory refinement:** products from the last assistant grid; the backend narrows **only within this list** (no fresh full-catalog search for that step). |
| `useSemanticReranking` | When `productPool` is sent: allow Vertex semantic reranking (server default applies if omitted). |

Exact Java types and validation: **`ChatRequest.java`**, **`ChatController.java`**.

## Response shape

**`ChatResponse`** includes `text`, `conversationId`, `refinedQuery`, `products`, `suggestedAnswers`, `clarifyingQuestion`, `rawResponse`, pagination fields (`productNextPageToken`, `productTotalSize`, …), and metadata like `queryType` and `source`.

On the **first** catalog population, **`productNextPageToken`** is usually **omitted**; page **`products`** in the app. **`productPageToken`** on requests does **not** trigger further Retail listing ([product-search-and-retail-apis.md](product-search-and-retail-apis.md)).

See **[CODE.md](../CODE.md)** for example JSON and frontend **Message** / **ProductDto** types.

## Product grid: local paging vs “Load more”

The web UI (`MessageList`) reconciles two behaviors:

- **No `productNextPageToken` (typical PoC listing):** the client keeps the full **`products`** array but initially shows only **`productPageSize`** items (default **20**), with a **Show more** control that reveals the next slice until all in-memory products are visible. This matches backend guidance to avoid Retail listing continuation.
- **`productNextPageToken` present and `onLoadMore` wired:** the grid shows the full current **`products`** list for that turn and a **Load more** button sends **`productPageToken`** to the server (legacy path; server still does not call Retail for extra pages in the default setup—see [product-search-and-retail-apis.md](product-search-and-retail-apis.md)).

`productPageSize` is sent on **`ChatRequest`** as both a server heuristic and the client’s slice/step size for **Show more**.

## Suggested-answer chips

Rendering and ingest rules (`useChat`, `MessageList`, **`suggestionVisibility.ts`**):

- For **small catalogs** (known **`productTotalSize`** ≤ **`productPageSize`**), the client **omits redundant facet-style chips** when building the assistant message—**unless** the turn is clearly a follow-up: **`clarifyingQuestion`** is set, **`text`** contains **`?`**, or **`shouldHideSuggestedAnswersForMessage`** otherwise allows chips. That prevents stripping real follow-up options from **`suggestedAnswers`**.
- With **products**, chips appear under the grid when **`clarifyingQuestion`** is set, or when **`clarifyingQuestion`** is absent but **`text`** includes **`?`** and **`suggestedAnswers`** are non-empty.
- Without products, chips render under the main bubble as before.

The UI still honors an **explicit empty** `suggestedAnswers` array from the server (no recovery from `rawResponse` for that turn).

## Product detail popover

**`ProductCard`**: hovering the image (or placeholder) opens a portaled detail tooltip. Horizontal position is **clamped to the viewport** (estimated width aligned with CSS `min(320px, 90vw)`); see **`productCardPopoverPlacement.ts`**.

## Voice input and voice output (Google Chrome only)

The **microphone** and **speaker** controls are **disabled** unless the app detects **Google Chrome** (desktop: `Chrome/` + `vendor === 'Google Inc.'`, excluding Edge/Opera; iOS: `CriOS/`). Hovering shows a short message to use Chrome for that feature.

Implementation: **`frontend/src/utils/chromeVoiceSupport.ts`** (`isGoogleChrome`, tooltip strings). **`VoiceInput`**, **`VoiceOutputToggle`**, and **`ChatInterface`** apply this behavior.

## Raw panel

**`RawResponsePanel`** shows **`rawResponse`** (and related history) for debugging conversational GCP payloads—useful when tracing refined queries and suggested answers.
