# Conversational Filtering Agent

A full-stack application (Spring Boot + React) that provides a chat UI to interact with Google's GCP Conversational Commerce agent, with a pluggable agent architecture supporting two orchestration modes.

## Architecture

- **Approach A (Convo Commerce as Orchestrator)**: Conversational Commerce API is primary; routes general questions to an ADK specialist agent.
- **Approach B (ADK as Orchestrator)**: ADK LlmAgent orchestrates; uses Conversational Commerce as a tool for product search, plus loyalty/recommendation tools.

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

**REST API docs (Swagger UI):** http://localhost:8080/swagger-ui.html  
**OpenAPI spec:** http://localhost:8080/v3/api-docs

POST `/api/chat`:

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
