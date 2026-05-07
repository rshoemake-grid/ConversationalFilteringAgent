import type { ChatRequest, ChatResponse } from './types';

const API_BASE = import.meta.env.VITE_API_URL ?? '/api';

interface ProblemDetail {
  title?: string;
  detail?: string;
}

async function parseErrorResponse(response: Response): Promise<string> {
  const text = await response.text();
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/json') && text) {
    try {
      const body = JSON.parse(text) as ProblemDetail;
      return body.detail ?? body.title ?? (text || `HTTP ${response.status}`);
    } catch {
      // use text as fallback
    }
  }
  return text || `HTTP ${response.status}`;
}

const NETWORK_ERROR_HINT =
  'Backend may be offline. Ensure the backend is running (e.g. ./run-app.sh or cd backend && ./mvnw spring-boot:run).';

export async function sendChatMessage(request: ChatRequest): Promise<ChatResponse> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    if (msg === 'Failed to fetch' || msg.includes('NetworkError') || msg.includes('Load failed')) {
      throw new Error(`Cannot reach the API. ${NETWORK_ERROR_HINT}`);
    }
    throw err;
  }

  if (!response.ok) {
    const message = await parseErrorResponse(response);
    throw new Error(message);
  }

  return response.json();
}
