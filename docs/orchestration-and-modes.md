# Orchestration modes

Chat requests include a **`mode`** field. **`OrchestratorService`** routes to one of two orchestrators.

## `convo_commerce` (Approach A)

- **Orchestrator:** `ConvoCommerceOrchestrator`
- **Behavior:** Delegates every turn to **`ConversationalCommerceAdapter`**, which:
  1. Calls **`ConversationalCommerceClient`** (conversational search).
  2. When appropriate, calls **`RetailSearchClient`** for products (see **[product-search-and-retail-apis.md](product-search-and-retail-apis.md)**).
- **Gemini / ADK:** Not the main router for this path. The adapter contains a branch that would use **`ClarifyingQuestionGenerator`** (Gemini) when `orchestrationMode` is not `convo_commerce`; **`OrchestratorService` only attaches `ConversationalCommerceAdapter` for `convo_commerce`**, so that branch is **inactive** in the default wiring.

The codebase also defines a **`GeneralQuestionSpecialist`** (Gemini via ADK-style runner) intended for **GENERAL_QUESTION** handling; it is **not** referenced from **`ConversationalCommerceAdapter`** today—shopping vs chit-chat still flows through conversational search and adapter logic.

## `adk_orchestrator` (Approach B)

- **Orchestrator:** `AdkOrchestrator`
- **Behavior:** Runs an **ADK `LlmAgent`** (`InMemoryRunner`) with tools such as **`ConversationalCommerceTool`** (conversational search metadata) and loyalty/recommendation tools. The model decides when to call tools.
- **Requirement:** **`GOOGLE_API_KEY`** (and ADK agent bean from **`AdkConfig`**). If the key is missing, a stub response asks you to set the key.

Product **grids** in the UI still ultimately depend on how tool results and follow-up logic map into **`AgentResponse`**; the primary **two-call Retail pattern** (conversational + search) is documented for **`ConversationalCommerceAdapter`**. When using ADK, inspect tool implementations and orchestration responses for the exact path.

## Context passed into the adapter

`OrchestratorService` builds a **context** map (visitor/session id, image, max suggested answers, previous assistant text, suggested answers, refined query, product page token, filter, page size). **`ConversationalCommerceAdapter`** reads these keys for recovery, pagination, and filtering.

Details of HTTP fields: **[frontend-and-chat-api.md](frontend-and-chat-api.md)**.

## Related code

| Class | Role |
|-------|------|
| `OrchestratorService` | `switch (mode)` routing |
| `ConvoCommerceOrchestrator` | Thin wrapper around `ConversationalAgent` |
| `AdkOrchestrator` | ADK session + `runAsync` |
| `ConversationalCommerceTool` | ADK tool wrapping conversational search |
| `ConversationalCommerceAdapter` | Full GCP conversational + product search pipeline |
