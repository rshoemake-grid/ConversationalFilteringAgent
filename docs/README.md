# Documentation index

Narrative documentation for how the Conversational Filtering Agent works. Prefer these topic-specific pages over one long document.

| Document | What it covers |
|----------|----------------|
| [user-flow-and-services.md](user-flow-and-services.md) | **User journeys** (chips, load more, image) and **which services/APIs run** in each orchestration mode |
| [system-overview.md](system-overview.md) | Stack, major components, end-to-end path from browser to Google Cloud |
| [product-search-and-retail-apis.md](product-search-and-retail-apis.md) | **Why there are two GCP calls** (conversational vs product search), pagination, enrichment, transports |
| [orchestration-and-modes.md](orchestration-and-modes.md) | `convo_commerce` vs `adk_orchestrator`, orchestrators, tools |
| [frontend-and-chat-api.md](frontend-and-chat-api.md) | React UI, `POST /api/chat`, important request fields for multi-turn |

**Also in the repo**

| Document | Purpose |
|----------|---------|
| [../README.md](../README.md) | Quick start, prerequisites |
| [../CONFIG.md](../CONFIG.md) | Credentials, env vars, GCP console setup |
| [../CODE.md](../CODE.md) | Package layout, API shapes, configuration tables |
| [../DEPLOY.md](../DEPLOY.md) | Docker, Kubernetes, CI |

Suggested reading order: **system-overview** → **user-flow-and-services** → **product-search-and-retail-apis** → **orchestration-and-modes** → **frontend-and-chat-api**.
