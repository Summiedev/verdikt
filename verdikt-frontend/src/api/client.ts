const apiBaseUrl = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

export interface ApiErrorBody {
  code?: string;
  errorCode?: string;
  message?: string;
  requestId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId?: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message || 'The request could not be completed.');
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? body.errorCode ?? 'REQUEST_FAILED';
    this.requestId = body.requestId;
  }
}

export interface ApiRequestOptions extends RequestInit {
  playerToken?: string;
}

export function apiUrl(path: string): string {
  return `${apiBaseUrl}${path}`;
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.playerToken) headers.set('X-Player-Token', options.playerToken);

  const response = await fetch(apiUrl(path), {
    ...options,
    headers,
  });

  const raw = await response.text();
  let body: unknown = undefined;
  if (raw) {
    try {
      body = JSON.parse(raw) as unknown;
    } catch {
      body = undefined;
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, (body ?? {}) as ApiErrorBody);
  }

  return (body ?? undefined) as T;
}
