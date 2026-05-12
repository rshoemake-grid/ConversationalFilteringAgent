# Conversational Filtering Agent

A full-stack application (Spring Boot + React) that provides a chat UI to interact with Google's GCP Conversational Commerce agent, with a pluggable agent architecture supporting two orchestration modes.

## Architecture

- **Approach A (`convo_commerce`)**: `ConversationalCommerceAdapter` calls **Retail conversationalSearch** for intent and follow-ups, then **Retail Search** for the product grid (and optional **Product.get** enrichment). No Gemini/ADK in the default wiring for this path.
- **Approach B (`adk_orchestrator`)**: **Gemini** (ADK `LlmAgent`) orchestrates the turn; **`ConversationalCommerceTool.searchProducts`** calls the same **conversationalSearch** API; **`VaisrRetailProductResolver`** fills products via **Retail Search** and aligns UX with the adapter. Additional tools (loyalty, recommendations) may be attached to the agent.

## Prerequisites

- Java 17+
- Node.js 18+
- Maven (or use `./mvnw` wrapper)
- For GCP integration: Google Cloud project with Vertex AI Search for commerce enabled

## Quick Start

### Run both (recommended)

**Linux/macOS:**
```bash
./run-app.sh
```

**Windows:**
```cmd
run-app.bat
```

Starts the backend (http://localhost:8080) and frontend (http://localhost:5173). On Windows, close the Backend and Frontend windows to stop.

### Or run separately

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```
Windows: `run-backend.bat`

The API runs at http://localhost:8080. GCP credentials and project config are required for product search (see CONFIG.md).

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```
Windows: `run-frontend.bat`

The UI runs at http://localhost:5173 and proxies `/api` to the backend.

### API Keys & Configuration

See **[CONFIG.md](CONFIG.md)** for full details. Summary:

| Credential | Where | Purpose |
|------------|-------|---------|
| `GOOGLE_API_KEY` | Environment variable | ADK/Gemini (Approach B, general Q&A) |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to service account JSON | GCP Retail API |
| `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` | Environment or `application.yml` | GCP project config |

**Minimum for Approach B:** Set `GOOGLE_API_KEY` (get from [Google AI Studio](https://aistudio.google.com/)).

**For real product search:** Also enable GCP Retail (see CONFIG.md).

## Running Tests

**All tests (backend + frontend):**
```bash
./run-tests.sh
```
Windows: `run-tests.bat`

**Backend only:**
```bash
cd backend
./mvnw test
```

## API

**Human-readable reference:** [docs/api-reference.md](docs/api-reference.md) (endpoints, fields, error shape).

**Interactive docs (when the server is running):**

| Resource | URL |
|----------|-----|
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs |

POST `/api/chat` (minimal example — full field list in **[docs/api-reference.md](docs/api-reference.md)**):
```json
{
  "mode": "convo_commerce" | "adk_orchestrator",
  "message": "user message",
  "conversationId": "optional for follow-up",
  "sessionId": "optional for visitor tracking"
}
```

Response:

```json
{
  "text": "assistant response",
  "conversationId": "session id",
  "refinedQuery": "optional search query",
  "products": []
}
```

## Documentation

| Document | Description |
|----------|-------------|
| [docs/README.md](docs/README.md) | **Start here** — index of topic guides (GCP two-call flow, modes, frontend/API) |
| [docs/api-reference.md](docs/api-reference.md) | **REST API** — `/api/chat`, `/api/models`, OpenAPI/Swagger, errors |
| [docs/user-flow-and-services.md](docs/user-flow-and-services.md) | User flows and which services (Retail, Gemini, tools) run per mode |
| [CODE.md](CODE.md) | Code architecture, API reference, data models, key flows |
| [DEPLOY.md](DEPLOY.md) | Docker, Kubernetes, Docker Compose, GCP credentials, CI/CD |
| [CONFIG.md](CONFIG.md) | API keys, environment variables, local setup |

## Project Structure

```
ConversationalFilteringAgent/
├── backend/           # Spring Boot
│   ├── agent/         # ConversationalAgent, adapters
│   ├── orchestration/ # ConvoCommerceOrchestrator, AdkOrchestrator
│   ├── tool/          # ADK tools (ConversationalCommerceTool, LoyaltyRecommendationTool)
│   └── web/           # ChatController
├── frontend/          # React + Vite
│   └── src/
│       ├── api/       # chatApi
│       └── components/ # ChatInterface, MessageList, ModeSelector
└── README.md
```
