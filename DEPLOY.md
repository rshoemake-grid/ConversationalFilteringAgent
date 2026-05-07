# Deployment Guide

DevOps documentation for deploying the Conversational Filtering Agent.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Docker](#docker)
- [Docker Compose](#docker-compose)
- [Kubernetes](#kubernetes)
- [GCP Credentials](#gcp-credentials)
- [Environment Variables](#environment-variables)
- [CI/CD Considerations](#cicd-considerations)
- [Troubleshooting](#troubleshooting)

---

## Overview

The application has **separate** frontend and backend services:

| Service | Build | Runtime | Port |
|---------|-------|---------|------|
| **Frontend** | Node 20, Vite | nginx:alpine | 80 |
| **Backend** | Java 17, Maven | Eclipse Temurin JRE | 8080 |
| **Proxy** (optional) | — | nginx:alpine | 80 → routes to frontend + backend |

The frontend calls the backend via relative URLs (`/api/chat`). A reverse proxy or Ingress routes `/` to the frontend and `/api`, `/swagger-ui`, `/v3` to the backend.

---

## Prerequisites

- Docker 20.10+
- (For K8s) kubectl, Kubernetes cluster
- GCP project with Vertex AI Search for commerce enabled
- Service account or Application Default Credentials

---

## Docker

### Build Images

From the **project root**:

```bash
# Frontend (serves static SPA via nginx)
docker build -t conversational-filtering-agent-frontend:latest \
  -f frontend/Dockerfile frontend/

# Backend (Spring Boot JAR)
docker build -t conversational-filtering-agent-backend:latest \
  -f backend/Dockerfile backend/
```

### Run Individually

```bash
# Backend only
docker run -p 8080:8080 \
  -e GCP_PROJECT_ID=your-project-id \
  conversational-filtering-agent-backend:latest

# Frontend only (needs backend at same host for /api; use proxy for local testing)
docker run -p 3000:80 conversational-filtering-agent-frontend:latest
```

### Push to Registry

```bash
# Example: Google Container Registry
docker tag conversational-filtering-agent-frontend:latest \
  gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest

docker tag conversational-filtering-agent-backend:latest \
  gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest
```

---

## Docker Compose

Runs frontend, backend, and an nginx proxy in one command.

### Start

```bash
docker compose up --build
```

App available at **http://localhost:8080**. The proxy routes:
- `/` → frontend
- `/api`, `/swagger-ui`, `/v3` → backend

### GCP Credentials

Uncomment in `docker-compose.yml`:

```yaml
backend:
  env_file:
    - backend/.env
  volumes:
    - ./gcp-credentials.json:/secrets/key.json:ro
  environment:
    - GOOGLE_APPLICATION_CREDENTIALS=/secrets/key.json
```

Create `backend/.env` with:
```bash
GCP_PROJECT_ID=your-project-id
GCP_PLACEMENT=projects/.../placements/default_search
GCP_BRANCH=projects/.../branches/default_branch
```

### Stop

```bash
docker compose down
```

---

## Kubernetes

### Manifests

| File | Purpose |
|------|---------|
| `k8s/configmap.yaml` | GCP project, transport, `APP_SERVE_FRONTEND` |
| `k8s/deployment-frontend.yaml` | Frontend Deployment |
| `k8s/deployment-backend.yaml` | Backend Deployment |
| `k8s/service-frontend.yaml` | ClusterIP for frontend |
| `k8s/service-backend.yaml` | ClusterIP for backend |
| `k8s/ingress.yaml` | Ingress (optional) |

### Build and Deploy

```bash
# 1. Build images (see Docker section above)

# 2. Push to registry your cluster can pull from
docker tag conversational-filtering-agent-frontend:latest \
  gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest

docker tag conversational-filtering-agent-backend:latest \
  gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest

# 3. Update image in manifests (or use image tags)
# Edit deployment-frontend.yaml and deployment-backend.yaml:
#   image: gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest
#   imagePullPolicy: Always

# 4. Apply manifests
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment-frontend.yaml
kubectl apply -f k8s/deployment-backend.yaml
kubectl apply -f k8s/service-frontend.yaml
kubectl apply -f k8s/service-backend.yaml
kubectl apply -f k8s/ingress.yaml   # if using Ingress
```

### Ingress Routing

With Ingress, the same host serves both:
- `/` → frontend
- `/api`, `/swagger-ui`, `/v3` → backend

Edit `k8s/ingress.yaml`:
- Change `host: your-domain.com` to your domain or remove for default
- Adjust annotations for your ingress controller (e.g. nginx, traefik)

### Local Access (no Ingress)

```bash
# Frontend
kubectl port-forward svc/conversational-filtering-agent-frontend 3000:80

# Backend
kubectl port-forward svc/conversational-filtering-agent-backend 8080:80
```

Use a reverse proxy or set `VITE_API_URL` when building the frontend to point at the backend.

### Scaling

```bash
kubectl scale deployment conversational-filtering-agent-frontend --replicas=3
kubectl scale deployment conversational-filtering-agent-backend --replicas=3
```

---

## GCP Credentials

The backend needs GCP credentials for the Retail API.

### Option 1: Workload Identity (GKE)

On GKE, use [Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity) so pods use a service account without mounting keys. No secret needed.

### Option 2: Secret with Service Account Key

1. Create key:
   ```bash
   gcloud iam service-accounts keys create key.json \
     --iam-account=your-sa@project.iam.gserviceaccount.com
   ```
2. Create secret:
   ```bash
   kubectl create secret generic gcp-credentials --from-file=key.json=key.json
   ```
3. Uncomment volume and volumeMount in `k8s/deployment-backend.yaml`.
4. Set env: `GOOGLE_APPLICATION_CREDENTIALS: /secrets/key.json`

### Option 3: ConfigMap (non-secret)

For project ID and placement only. Credentials must come from a secret or Workload Identity.

---

## Environment Variables

### Backend

| Variable | Required | Description |
|----------|----------|-------------|
| `GCP_PROJECT_ID` | Yes* | GCP project ID |
| `GCP_PLACEMENT` | Yes* | Placement path |
| `GCP_BRANCH` | Yes* | Branch path |
| `GOOGLE_APPLICATION_CREDENTIALS` | Yes* | Path to service account JSON |
| `GOOGLE_API_KEY` | No | For Gemini/ADK |
| `GEMINI_REFERER` | No | For restricted API keys |
| `CONVERSATIONAL_COMMERCE_TRANSPORT` | No | `rest` (default) or `grpc` |
| `CONVERSATIONAL_FILTERING_MODE` | No | `ENABLED` (default) or `DISABLED` |
| `APP_SERVE_FRONTEND` | No | `false` for separate deployment |

*Required for full product search.

### Frontend

| Variable | Required | Description |
|----------|----------|-------------|
| `VITE_API_URL` | No | API base URL (default: `/api`) |

Set at **build time** for production:
```bash
VITE_API_URL=https://api.example.com/api npm run build
```

---

## CI/CD Considerations

### Build Pipeline

1. **Lint & Test**
   ```bash
   cd backend && ./mvnw test
   cd frontend && npm ci && npm test
   ```
2. **Build Images**
   ```bash
   docker build -t $IMAGE_FRONTEND -f frontend/Dockerfile frontend/
   docker build -t $IMAGE_BACKEND -f backend/Dockerfile backend/
   ```
3. **Push**
   ```bash
   docker push $IMAGE_FRONTEND
   docker push $IMAGE_BACKEND
   ```
4. **Deploy**
   - `kubectl set image` or re-apply manifests
   - Or use Helm, Argo CD, etc.

### Health Checks

- **Frontend:** `GET /health` returns 200.
- **Backend:** `GET /swagger-ui/index.html` or `GET /api/models` for liveness/readiness.

### Resource Limits

Default in K8s manifests:
- Frontend: 64Mi–128Mi memory, 50m–100m CPU
- Backend: 256Mi–512Mi memory, 250m–500m CPU

Adjust in `deployment-*.yaml` as needed.

---

## Troubleshooting

### "Failed ALPN negotiation"

Use REST transport: set `CONVERSATIONAL_COMMERCE_TRANSPORT=rest` (default in K8s ConfigMap).

### CORS errors

Set `app.cors.allowed-origins` to your frontend origin (e.g. `https://app.example.com`).

### Frontend can't reach backend

- **Docker Compose:** Proxy routes `/api` to backend. Ensure proxy is running.
- **K8s:** Ingress must route `/api` to backend service.
- **Standalone:** Set `VITE_API_URL` when building frontend.

### GCP "UNAUTHENTICATED"

- Verify `GOOGLE_APPLICATION_CREDENTIALS` path and file permissions.
- For Workload Identity, ensure pod has correct annotation and K8s SA is bound.

### Image pull errors

- Use `imagePullSecrets` for private registries.
- Ensure `imagePullPolicy: Always` when using mutable tags like `latest`.
