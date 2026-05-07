# Kubernetes Deployment

Deploy the Conversational Filtering Agent (frontend + backend) to Kubernetes.

## Build Images

Build from the **project root**:

```bash
# Frontend (context: frontend/)
docker build -t conversational-filtering-agent-frontend:latest -f frontend/Dockerfile frontend/

# Backend (context: backend/)
docker build -t conversational-filtering-agent-backend:latest -f backend/Dockerfile backend/
```

## Deploy

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment-frontend.yaml
kubectl apply -f k8s/deployment-backend.yaml
kubectl apply -f k8s/service-frontend.yaml
kubectl apply -f k8s/service-backend.yaml
kubectl apply -f k8s/ingress.yaml  # Optional: for external access
```

## Local Access (without Ingress)

Port-forward both services:

```bash
# Terminal 1: frontend on 3000
kubectl port-forward svc/conversational-filtering-agent-frontend 3000:80

# Terminal 2: backend on 8080
kubectl port-forward svc/conversational-filtering-agent-backend 8080:80
```

Then set `VITE_API_URL=http://localhost:8080/api` when building the frontend, or use a reverse proxy. For local dev, it's easier to run frontend and backend separately with `npm run dev` and `./mvnw spring-boot:run`.

## Ingress Routing

The Ingress routes:

- `/`, `/health` → frontend (SPA)
- `/api`, `/swagger-ui`, `/v3` → backend

The frontend uses relative URLs (`/api/chat`), so when served through the Ingress, API calls go to the same host and are proxied to the backend. No `VITE_API_URL` needed when using Ingress.

## GCP Credentials

The backend needs GCP credentials for the Retail API. Options:

### Option 1: Workload Identity (GKE)

Use [Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity) so the pod inherits a service account. No secret mount needed.

### Option 2: Secret with Service Account Key

1. Create a key: `gcloud iam service-accounts keys create key.json --iam-account=your-sa@project.iam.gserviceaccount.com`
2. Create secret: `kubectl create secret generic gcp-credentials --from-file=key.json=key.json`
3. Uncomment the volume and volumeMount in `deployment-backend.yaml`
4. Add env to the backend deployment: `GOOGLE_APPLICATION_CREDENTIALS: /secrets/key.json`

### Option 3: API Key (limited)

For Gemini only, set `GOOGLE_API_KEY` env. Retail API typically requires ADC.

## ConfigMap

Edit `configmap.yaml` to set your GCP project ID, placement, and branch before applying.

## Image Registry

Push to a registry for a real cluster:

```bash
docker tag conversational-filtering-agent-frontend:latest gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-frontend:latest

docker tag conversational-filtering-agent-backend:latest gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest
docker push gcr.io/YOUR_PROJECT/conversational-filtering-agent-backend:latest
```

Update the image fields in `deployment-frontend.yaml` and `deployment-backend.yaml`, and set `imagePullPolicy: Always`.
