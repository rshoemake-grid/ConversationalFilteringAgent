# Product search and Retail APIs

This document explains **how product lists are produced** and why you see **more than one Google Cloud call** for a single user message in **`convo_commerce`** mode. For **user journeys** (chips, product grid, refinement), see **[user-flow-and-services.md](user-flow-and-services.md)**. For **HTTP** fields and OpenAPI links, see **[api-reference.md](api-reference.md)**.

## PoC: first hit vs refinement

For **proof-of-concept / demo** deployments using the **catalog as the source list** (instead of a customer API handing you products):

- The **first** Retail Search for a query uses **`InitialCatalogAggregator`**: a large per-request page size (default **100**, API typical max), optional **multi-page merge** until caps are hit (`conversational-commerce.initial-catalog-*` in `application.yml`). By default, pages are pulled **in parallel** using Retail **`offset`** (configurable); set **`initial-catalog-parallel-page-fetches: false`** to use a sequential **`nextPageToken`** walk instead.
- By default the response **omits `productNextPageToken`** for that first population so the **web UI does not call Google for grid paging**—slice or page the **`products`** array in your **app/API layer** instead.
- **Follow-up refinement** on the same thread in Approach A uses the **`productPool`** from the prior assistant message (see **[frontend-and-chat-api.md](frontend-and-chat-api.md)**) and **`refineInMemoryProductPool`** — conversational search still runs for intent, but the **shown products are narrowed only from the pool**, not a fresh full-catalog listing.

`product-search-page-size` and the request field **`productPageSize`** influence **server heuristics** (e.g. approximate totals, ingest-time rules for when to clear **`suggestedAnswers`**) and the **web client’s local product grid paging**: initial visible count and **Show more** step size when **`productNextPageToken`** is absent. They do **not** cap the first catalog merge on the server and are **not** used to call Retail for listing continuation.

## Two different Retail capabilities

| Step | API (conceptual) | Client in code | Primary purpose |
|------|------------------|----------------|-----------------|
| 1 | **Conversational Search** (`conversationalSearch`) | `ConversationalCommerceClient` | Multi-turn conversation, **refined search query**, suggested answers, query classification, assistant text |
| 2 | **Retail Search** (search products on a branch) | **`InitialCatalogAggregator`** + **`RetailSearchClient`** | **Product listing** for a refined query: merged first pull only (no Retail “load more”) |

They use the same **catalog** (your branch and placement), but **different RPCs/HTTP endpoints**. The conversational response **does not replace** the product search step in this application: **`InitialCatalogAggregator`** drives the **first** product listing from **`RetailSearchClient`** when there is a usable **refined query** (with exceptions below). **`ConversationalCommerceAdapter`** does not call **`RetailSearchClient`** directly; subsequent grid paging is **not** implemented with further Retail list calls.

## Sequence (typical turn)

1. **`ConversationalCommerceAdapter`** builds a `ConversationalCommerceRequest` (placement, branch, user text, visitor id, conversation id, optional image).
2. **`client.search(request)`** calls **conversational search** (REST: `https://retail.googleapis.com/v2/{placement}:conversationalSearch`, or gRPC equivalent when `transport=grpc`).
3. The result includes **`refinedQuery`**, **`conversationId`**, **`queryType`**, **`suggestedAnswers`**, **`rawResponse`**, and text used for the reply.
4. If **`refinedQuery`** is non-empty and the turn is **not** an in-memory **`productPool`** refine, **`InitialCatalogAggregator`** calls **`RetailSearchClient.searchWithPagination`** with **`offset`** in parallel (default) or **`nextPageToken`** sequentially (when **`initial-catalog-parallel-page-fetches`** is false), merging into one list up to **`initial-catalog-*`** caps. **`productPageSize`** does **not** cap this merge.
5. After the merged listing, **`ProductEnrichmentService`** may call **Product.get** for hits with sparse description/price. Those calls run **in parallel** (bounded pool, same scale as **`initial-catalog-max-concurrent-requests`**) so large catalogs are not enriched one-by-one on the servlet thread (which previously could keep the UI on “Searching…” for many minutes). **Product.get** is **not** blocked by **`retail-single-shot-per-conversation`**. **`OrchestratorService`** still wraps each HTTP turn with **`RetailProductApiGate`**; **`BrandDisplayResolver`** Retail Search lookups **are** gated after the first listing (see below).

If the client sends **`productPageToken`** (legacy “load more”), **`ConversationalCommerceAdapter`** responds **without** calling Retail Search or conversational search again: the full hit set for that search was already returned on the initial listing (within configured caps). Page products in your UI or API.

**In-memory refinement** turns (client sends **`productPool`**) still run conversational search for intent, but **narrowing** uses **`ProductPoolNarrower`** against the pool only—no fresh full-catalog listing for that step.

So **yes: the product grid is loaded by Retail Search**, not by reusing conversational search as the sole source of product hits.

## Legacy `productPageToken` (no Retail continuation)

Older clients may send **`productPageToken`**, **`previousRefinedQuery`**, and **`previousProductFilter`**. The backend **does not** issue further **Retail Search** list calls for that token. Use **`productNextPageToken`-free** responses and **local paging** of **`products`** instead.

## Configuration (first catalog merge)

YAML keys live under **`conversational-commerce`** in `application.yml`. See **`backend/src/main/resources/application.yml.example`** for env-style overrides.

| Key | Role |
|-----|------|
| **`initial-catalog-page-size`** | Page size per Retail Search request on the first fill (clamped to API max, typically **100**). |
| **`initial-catalog-fetch-all-pages`** | When **true**, merge multiple pages on the first listing (parallel **offset** or sequential **nextPageToken** per **`initial-catalog-parallel-page-fetches`**). |
| **`initial-catalog-max-products`** | Stop merging after this many **unique** products (by id). |
| **`initial-catalog-max-page-requests`** | Safety cap on how many search calls the first fill may perform. |
| **`initial-catalog-suppress-next-page-token`** | When **true**, omit **`productNextPageToken`** on the merged listing so clients rely on **local paging** of **`products`** (aligned with no Retail listing continuation). |
| **`initial-catalog-parallel-page-fetches`** | When **true** (default), parallel **`offset`** requests; when **false**, sequential **`nextPageToken`** (slower, token-order semantics). |
| **`initial-catalog-max-concurrent-requests`** | Thread pool bound for parallel catalog **offset** fetches (capped at **64** in code). Also caps concurrent **Product.get** enrichment calls. |
| **`retail-single-shot-per-conversation`** | When **true** (default), after the first initial catalog listing for a conversation/session, skip further **Retail Search** used for listing and for **brand display** lookup (see section below). **Product.get** enrichment still runs. |

## REST vs gRPC

- **`conversational-commerce.transport=rest`** (default in examples): `RetailConversationalSearchClientRest` + REST product search implementation. Useful when gRPC/ALPN fails (VPN, proxies).
- **`transport=grpc`**: `RetailConversationalSearchClient` (gRPC) + gRPC product search.

See `application.yml.example` and **[CONFIG.md](../CONFIG.md)**.

## What conversational search returns vs what you display

- **Conversational search** drives **what to search for** and **UX affordances** (chips, follow-up questions, conversation id).
- **Retail Search** drives **which SKUs** appear in the **initial merged** listing (**`productNextPageToken`** is not used for further Retail listing in this app).

**In-memory `productPool` turns:** the **displayed** grid is narrowed from the client-supplied pool only; Retail Search is not used to pull a new full-catalog slice for that refinement step. With **`retail-single-shot-per-conversation: true`** (default), if the session already completed an initial catalog listing, **`InitialCatalogAggregator`** returns an empty list when a turn would otherwise run a fresh listing; **`BrandDisplayResolver`** skips Retail Search for brand resolution on later turns (config display maps and title-case fallback still apply). **`ProductEnrichmentService`** still calls **Product.get** when needed.

## Retail product APIs: single shot per conversation (PoC)

When **`conversational-commerce.retail-single-shot-per-conversation`** is **true** (default), after the first turn that runs an **initial catalog listing** (merged Retail Search via **`InitialCatalogAggregator`**), the same **`conversationId`** (or **`sessionId`** if the conversation id is absent) will not trigger:

- further Retail **Search** list calls from **`InitialCatalogAggregator`**;
- brand display resolution via Retail **Search** in **`BrandDisplayResolver`**.

**Product.get** in **`ProductEnrichmentService`** is **not** included: enrichment still runs on later turns when **`transport=rest`** and a **`ProductFetcher`** bean is available.

**Conversational Search** (`conversationalSearch`) is **not** gated. Set the flag to **false** for integration tests or tools that need multiple full listings in one conversation.

If conversational search returns an empty **refined query**, the adapter may still run recovery logic (e.g. **no-preference** / **storage-type** paths) using prior context from the HTTP request before giving up on a product search.

## Product.Get: which “key” is used?

Enrichment calls **`Product.get`** only when **`transport=rest`** and a **`ProductFetcher`** bean is active (`RetailProductFetcherRest`). This is **independent** of **`retail-single-shot-per-conversation`** (single-shot applies to **Retail Search** listing and brand lookup, not Product.get).

| Field on `AgentResponse.ProductResult` | Meaning | Used for Product.Get? |
|----------------------------------------|---------|------------------------|
| **`id`** | GCP **resource name** of the product — the API’s **`name`** field, e.g. `projects/…/locations/…/catalogs/…/branches/…/products/{productId}` | **Yes.** This is passed to `ProductFetcher.getProduct(String productName)` and requested as `GET https://retail.googleapis.com/v2/{name}`. |
| **`productId`** | Short id within the branch (often the last path segment, or JSON `id`) | **No** for Product.Get in this codebase. |

**Where `id` is set:** Search hits populate **`id`** from the Retail **`Product.name`** (gRPC: `product.getName()`; REST JSON: `"name"` on the product object via `ProductResponseParser`).

**When enrichment runs:** `ProductEnrichmentService` only fetches details if **`id` is non-blank and contains the substring `/products/`** and description or price is missing. Otherwise it skips Product.Get.

See **`ProductFetcher`**, **`ProductEnrichmentService`**, and **`RetailProductFetcherRest`** in the backend.

## Code entry points

| Concern | Class |
|---------|--------|
| Orchestrates both steps | `ConversationalCommerceAdapter` |
| Conversational HTTP/JSON | `RetailConversationalSearchClientRest` |
| Conversational gRPC | `RetailConversationalSearchClient` |
| First catalog merge (optional multi-page) | `InitialCatalogAggregator` |
| **Retail product API gate (PoC)** | `RetailProductApiGate` |
| Product search abstraction | `RetailSearchClient` (implementations: REST / gRPC) |
| Narrowing an existing grid | `ProductPoolNarrower` (in-memory; uses `productPool` from the client) |
| Optional Product.get | `ProductEnrichmentService`, `RetailProductFetcherRest` |

For package layout and DTOs, see **[CODE.md](../CODE.md)**.
