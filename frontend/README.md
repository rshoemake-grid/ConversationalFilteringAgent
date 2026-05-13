# Frontend (React + Vite)

Chat UI for the Conversational Filtering Agent. **`npm run dev`** serves at **http://localhost:5173** with **`/api`** proxied to the Spring Boot backend (see `vite.config.ts`).

**Behavior** (see **[docs/frontend-and-chat-api.md](../docs/frontend-and-chat-api.md)**):

- **Product grid:** **Show more** reveals the next slice of **`products`** when the server omits **`productNextPageToken`**; slice size comes from **`productPageSize`** (request field + hook state). **Load more** is used only for the legacy **`productNextPageToken`** path.
- **Suggested answers:** Chips under the product grid for **`clarifyingQuestion`** or for assistant **`text`** containing **`?`**; ingest-time stripping of small-catalog facet echoes skips real follow-ups (`useChat` + **`src/utils/suggestionVisibility.ts`**).
- **Product cards:** Hover tooltip with full details; horizontal position clamped to the viewport (`src/utils/productCardPopoverPlacement.ts`).

**Tests:** `npm test` (Vitest). Template note: this project extends the default Vite React template (ESLint, TS config); see root **[README.md](../README.md)** for full-stack setup.
