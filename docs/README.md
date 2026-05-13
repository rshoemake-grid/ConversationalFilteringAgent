# Documentation index

Narrative documentation for how the Conversational Filtering Agent works. Prefer these topic-specific pages over one long document.

| Document | What it covers |
|----------|----------------|
| [api-reference.md](api-reference.md) | **REST API** — endpoints, request/response fields, errors, OpenAPI/Swagger links |
| [user-flow-and-services.md](user-flow-and-services.md) | **User journeys** (chips, product grid, image) and **which services/APIs run** in each orchestration mode |
| [system-overview.md](system-overview.md) | Stack, major components, end-to-end path from browser to Google Cloud |
| [product-search-and-retail-apis.md](product-search-and-retail-apis.md) | **Two GCP calls** (conversational vs Retail Search), merged first listing, pool-only refinement, enrichment, transports |
| [orchestration-and-modes.md](orchestration-and-modes.md) | `convo_commerce` vs `adk_orchestrator`, orchestrators, tools |
| [frontend-and-chat-api.md](frontend-and-chat-api.md) | React UI, `POST /api/chat`, important request fields for multi-turn |

**Also in the repo**

| Document | Purpose |
|----------|---------|
| [../frontend/README.md](../frontend/README.md) | **Frontend-only** — Vite dev, UI behaviors (Show more, chips, popover) |
| [../CONFIG.md](../CONFIG.md) | Credentials, env vars, GCP console setup |
| [../CODE.md](../CODE.md) | Package layout, API shapes, configuration tables |
| [../DEPLOY.md](../DEPLOY.md) | Docker, Kubernetes, CI |

Suggested reading order: **system-overview** → **user-flow-and-services** → **api-reference** → **product-search-and-retail-apis** → **orchestration-and-modes** → **frontend-and-chat-api**.
