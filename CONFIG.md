# API Keys and Configuration

How Retail **conversational search** and **product search** calls fit together: **[docs/product-search-and-retail-apis.md](docs/product-search-and-retail-apis.md)**.

## Application Config

`application.yml` is not committed (it may contain project-specific values). Copy the example to create your own:

```bash
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application.yml
```

Then edit `application.yml` with your settings.

---

## Summary

| What | Key/Credential | Where to Put |
|------|----------------|--------------|
| **ADK / Gemini** (Approach B, general Q&A) | `GOOGLE_API_KEY` | Environment variable |
| **Gemini model** | `GEMINI_MODEL` or `app.gemini.model` | Default: `gemini-flash-latest` |
| **GCP Retail / Conversational Commerce** | Service account JSON or Application Default Credentials | `GOOGLE_APPLICATION_CREDENTIALS` or `app.gcp.credentials-path` |
| **GCP project settings** | `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` | Environment variables or `application.yml` |

---

## 1. ADK / Gemini (for Approach B and General Q&A specialist)

**Used by:** ADK orchestrator agent, General Question specialist (Approach A)

**Note:** The app runs without `GOOGLE_API_KEY` by default. Approach B returns a placeholder message and logs a warning. Set `GOOGLE_API_KEY` for full functionality.

**Get a key:**
1. Go to [Google AI Studio](https://aistudio.google.com/)
2. Click **Get API key** → Create API key
3. Copy the key

**Where to put it:**

```bash
export GOOGLE_API_KEY="your-api-key-here"
```

To make it permanent, add to `~/.zshrc` or `~/.bash_profile`:
```bash
echo 'export GOOGLE_API_KEY="your-api-key-here"' >> ~/.zshrc
source ~/.zshrc
```

Or create a `.env` file in the project root and load it before running:
```bash
# .env (add to .gitignore - do not commit)
GOOGLE_API_KEY=your-api-key-here
```

Then run: `source .env && ./run-app.sh`

---

## 2. GCP Retail / Conversational Commerce API

**Used by:** Approach A (Convo Commerce as orchestrator), and the product search tool in Approach B

**Prerequisites:**
- GCP project with [Vertex AI Search for commerce](https://cloud.google.com/retail/docs/conversational-commerce-dev-guide) enabled
- Retail catalog and placement configured

**Option A: Application Default Credentials (recommended for local dev)**

```bash
gcloud auth application-default login
```

This uses your user credentials. No separate key file.

**Option B: Service account JSON**

1. In [GCP Console](https://console.cloud.google.com/) → IAM & Admin → Service Accounts
2. Create a service account with **Vertex AI User** and **Retail Admin** (or equivalent) roles
3. Create a key (JSON) and download it
4. Set the path:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your-service-account-key.json"
```

**Configurable path:** Set `app.gcp.credentials-path` in `application.yml` to override. Default: `C:/Users/rsho0016/AppData/Roaming/gcloud/application_default_credentials.json` (or `GOOGLE_APPLICATION_CREDENTIALS` if set). If the configured file exists, it is used; otherwise Application Default Credentials are used.

---

## 2b. Agent Handles Full Conversation (Approach A)

To let the **Conversational Commerce agent** handle the entire conversation—including asking clarifying questions when many products match—enable **Conversational Product Filtering**.

### 1. Enable in your app

In `application.yml` or via environment variable:

```yaml
conversational-commerce:
  conversational-filtering-mode: ENABLED
```

Or:

```bash
export CONVERSATIONAL_FILTERING_MODE=ENABLED
```

### 2. Enable in GCP Console

The agent needs to be configured in the [Vertex AI Search for commerce console](https://console.cloud.google.com/ai/retail/catalogs/default_catalog/conversational_search/):

1. Go to **Conversational product filtering and browse**
2. Meet data requirements (25% conversational coverage, 1,000+ queries/day, product attributes as indexable/dynamically facetable)
3. Configure AI-generated questions under **Manage AI generated questions**
4. Set the minimum products threshold (e.g. 2) that triggers follow-up questions
5. Turn the toggle **On**

See the [Conversational filtering developer's guide](https://cloud.google.com/retail/docs/conversational-filtering-dev-guide) for details.

### 3. Troubleshooting: Getting confusing questions instead of products

If the GCP Console returns products for "I'm looking for rice" but this app shows follow-up questions and no products:

- **Raise the minimum products threshold** in GCP Console (e.g. from 2 to 10 or 20). The agent only asks follow-up questions when the number of matching products exceeds this threshold. A higher value means products are shown more often before filtering kicks in.
- **Check placement and branch** — Ensure `GCP_PLACEMENT` and `GCP_BRANCH` in this app match exactly what the GCP Console uses (same project, catalog, branch).
- **Verify the same catalog** — The Console may be testing against a different catalog or branch than your app.
- **Temporarily use DISABLED** — Set `CONVERSATIONAL_FILTERING_MODE=DISABLED` to bypass follow-up questions and return products directly (useful for debugging).

### 4. Troubleshooting: Agent repeats the same question (session/context)

If the agent keeps asking the same question (e.g. "Which brand would you prefer?") and ignores your answer:

- **Conversation ID** — The app sends `conversationId` from the previous response with each follow-up request. The GCP API requires this to maintain context. To verify it's working, enable debug logging:
  ```yaml
  logging:
    level:
      com.conversationalcommerce.agent.agent.RetailConversationalSearchClientRest: DEBUG
  ```
  You should see `requestConvId` and `responseConvId` in the logs. The first request has empty `requestConvId`; subsequent requests should have the `responseConvId` from the previous call.

- **GCP agent behavior** — The agent may only accept specific answers (e.g. catalog brand names) to "Which brand would you prefer?". Answers like "no preference" or "any" might not be recognized. Try answering with a specific brand from the catalog, or adjust AI-generated questions in the GCP Console.

---

## 3. GCP Project Configuration

**Environment variables:**

```bash
export GCP_PROJECT_ID="your-gcp-project-id"
export GCP_PLACEMENT="projects/your-project-id/locations/global/catalogs/default_catalog/placements/default_search"
export GCP_BRANCH="projects/your-project-id/locations/global/catalogs/default_catalog/branches/default_branch"
```

**Or** set in `backend/src/main/resources/application.yml` (avoid putting secrets here):

```yaml
conversational-commerce:
  project-id: your-project-id
  placement: projects/your-project-id/locations/global/catalogs/default_catalog/placements/default_search
  branch: projects/your-project-id/locations/global/catalogs/default_catalog/branches/default_branch
```

---

## 4. Quick Setup Checklist

**Approach B (ADK/Gemini):**
- `GOOGLE_API_KEY` – for real ADK orchestrator

**Full setup (real product search):**
- `GOOGLE_API_KEY` – for ADK/Gemini
- `GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth application-default login` – for Retail API
- `GCP_PROJECT_ID`, `GCP_PLACEMENT`, `GCP_BRANCH` – project config

---

## 6. Frontend API URL (production)

For local development, the Vite dev server proxies `/api` to the backend. For production builds, set the backend URL:

```bash
# In .env or when building
VITE_API_URL=https://your-backend.example.com/api
```

Create `frontend/.env.production` with `VITE_API_URL=...` before `npm run build`. If unset, the frontend uses `/api` (relative), which works when served from the same origin as the backend.

---

## 7. Example `.env` file (add to .gitignore)

```bash
# .env - DO NOT COMMIT
# Optional for production frontend:
# VITE_API_URL=https://your-backend.example.com/api
GOOGLE_API_KEY=AIza...
# Optional: override Gemini model (default: gemini-flash-latest)
# GEMINI_MODEL=gemini-2.5-pro
# GET /api/models lists available models (requires GOOGLE_API_KEY). Run: ./backend/list-models.sh
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
GCP_PROJECT_ID=my-project-123
GCP_PLACEMENT=projects/my-project-123/locations/global/catalogs/default_catalog/placements/default_search
GCP_BRANCH=projects/my-project-123/locations/global/catalogs/default_catalog/branches/default_branch
```

Load before running: `set -a && source .env && set +a && ./run-app.sh`

---

## 8. "Requests from referer <empty> are blocked" (403)

If your API key has **website restrictions** in Google Cloud Console, the backend must send a Referer header when calling Google's API. Set:

```bash
# Must match a whitelisted referrer in your API key restrictions (default: http://localhost:5173/)
export GEMINI_REFERER="http://localhost:5173/"
```

Or in `application.yml`: `app.gemini.referer: http://localhost:5173/`

**Alternative:** Remove website restrictions from the API key in [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → API Keys → your key → Application restrictions → set to "None" for server-side use.

---

## 9. Troubleshooting GCP Errors

### "Failed ALPN negotiation: Unable to find compatible protocol"

This occurs when the gRPC client cannot establish HTTP/2 with GCP, often due to **VPN** or **corporate proxy** that blocks or interferes with HTTP/2/ALPN.

**Try these in order:**

1. **Disable VPN** – If you're on a VPN, disconnect and try again.
2. **Use a different network** – Home WiFi, mobile hotspot, or coffee shop to bypass corporate proxy.
3. **Conscrypt (already enabled)** – The app registers Conscrypt as an alternative TLS/ALPN provider at startup. Restart the app and try again.
4. **JVM TLS args** – Already set by default. If needed manually:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Djdk.tls.client.protocols=TLSv1.2,TLSv1.3"
   ```

**5. Use REST transport:**
- Set `conversational-commerce.transport=rest` (or `CONVERSATIONAL_COMMERCE_TRANSPORT=rest`). This uses HTTP/1.1 REST instead of gRPC. Product search may still use gRPC and might fail. Conversational text should work.

**6. If none work:** The proxy/firewall likely blocks HTTP/2. Options:
- Run from **Google Cloud Shell** (direct GCP access)
- Ask IT to enable HTTP/2 for `retail.googleapis.com`
