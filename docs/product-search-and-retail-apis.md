# Product search and Retail APIs

This document explains **how product lists are produced** and why you see **more than one Google Cloud call** for a single user message in **`convo_commerce`** mode.

## Two different Retail capabilities

| Step | API (conceptual) | Client in code | Primary purpose |
|------|------------------|----------------|-----------------|
| 1 | **Conversational Search** (`conversationalSearch`) | `ConversationalCommerceClient` | Multi-turn conversation, **refined search query**, suggested answers, query classification, assistant text |
| 2 | **Retail Search** (search products on a branch) | `RetailSearchClient` | **Paged product list**, filters, total size / next page token |

They use the same **catalog** (your branch and placement), but **different RPCs/HTTP endpoints**. The conversational response **does not replace** the product search step in this application: the adapter **always** drives product listing from **`RetailSearchClient`** when it has a usable **refined query** (with exceptions below).

## Sequence (typical turn)

1. **`ConversationalCommerceAdapter`** builds a `ConversationalCommerceRequest` (placement, branch, user text, visitor id, conversation id, optional image).
2. **`client.search(request)`** calls **conversational search** (REST: `https://retail.googleapis.com/v2/{placement}:conversationalSearch`, or gRPC equivalent when `transport=grpc`).
3. The result includes **`refinedQuery`**, **`conversationId`**, **`queryType`**, **`suggestedAnswers`**, **`rawResponse`**, and text used for the reply.
4. If **`refinedQuery`** is non-empty, the adapter calls **`searchClient.searchWithPagination(...)`** with:
   - same **placement** and **branch**
   - **refined query** string
   - optional **filter** (e.g. brand / storage-type handling built in the adapter)
   - optional **page token** (for continuation)
   - optional **page size** override from the chat request context
5. **`ProductEnrichmentService`** may call **`Product.get`** (via `ProductFetcher`, REST path) to fill missing price/description on individual products.

So **yes: the product grid is loaded by a separate Retail Search call**, not by reusing conversational search as the sole source of product hits.

## Load more (pagination)

When the user clicks **Load more**, the frontend sends **`productPageToken`**, **`previousRefinedQuery`**, and **`previousProductFilter`** (see [frontend-and-chat-api.md](frontend-and-chat-api.md)).

The adapter **skips conversational search** for that request and calls **`RetailSearchClient.searchWithPagination`** directly with the stored query, filter, and token. That is still the **Retail Search API**, not another conversational turn.

## REST vs gRPC

- **`conversational-commerce.transport=rest`** (default in examples): `RetailConversationalSearchClientRest` + REST product search implementation. Useful when gRPC/ALPN fails (VPN, proxies).
- **`transport=grpc`**: `RetailConversationalSearchClient` (gRPC) + gRPC product search.

See `application.yml.example` and **[CONFIG.md](../CONFIG.md)**.

## What conversational search returns vs what you display

- **Conversational search** drives **what to search for** and **UX affordances** (chips, follow-up questions, conversation id).
- **Retail Search** drives **which SKUs** appear and **pagination metadata**.

If conversational search returns an empty **refined query**, the adapter may still run recovery logic (e.g. **no-preference** / **storage-type** paths) using prior context from the HTTP request before giving up on a product search.

## Product.Get: which “key” is used?

Enrichment calls **`Product.get`** only when **`transport=rest`** and a **`ProductFetcher`** bean is active (`RetailProductFetcherRest`).

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
| Product search abstraction | `RetailSearchClient` (implementations: REST / gRPC) |
| Optional Product.get | `ProductEnrichmentService`, `RetailProductFetcherRest` |

For package layout and DTOs, see **[CODE.md](../CODE.md)**.
