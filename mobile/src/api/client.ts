import { AuthTokens, CallResponse } from '../types';

export const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

type TokenHandlers = {
  getAccessToken: () => string | null;
  getRefreshToken: () => string | null;
  onTokens: (tokens: AuthTokens) => Promise<void>;
  onAuthFailure: () => Promise<void>;
};

async function refreshTokens(refreshToken: string): Promise<AuthTokens> {
  const response = await fetch(`${API_BASE_URL}/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });

  if (!response.ok) {
    throw new Error('Failed to refresh token');
  }

  return response.json();
}

async function extractErrorMessage(response: Response): Promise<string> {
  const raw = await response.text();
  if (!raw) {
    return `${response.status} ${response.statusText}`;
  }

  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.message === 'string' && parsed.message.trim()) {
      return parsed.message.trim();
    }
  } catch {
    // Ignore parse errors and fallback to raw text.
  }

  return raw;
}

export class ApiClient {
  private readonly tokenHandlers: TokenHandlers;

  constructor(tokenHandlers: TokenHandlers) {
    this.tokenHandlers = tokenHandlers;
  }

  async checkHealth(): Promise<boolean> {
    try {
      const response = await fetch(`${API_BASE_URL}/actuator/health`);
      return response.ok;
    } catch {
      return false;
    }
  }

  async login(username: string, pin: string): Promise<AuthTokens> {
    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, pin })
      });
    } catch {
      throw new Error(`Cannot reach backend at ${API_BASE_URL}`);
    }

    if (!response.ok) {
      throw new Error(await extractErrorMessage(response));
    }

    return response.json();
  }

  async logout(): Promise<void> {
    const refreshToken = this.tokenHandlers.getRefreshToken();
    if (!refreshToken) {
      return;
    }

    try {
      await fetch(`${API_BASE_URL}/v1/auth/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      });
    } catch {
      // Best-effort logout; local token cleanup still happens in AuthContext.
    }
  }

  async createCall(callAt: string): Promise<CallResponse> {
    return this.request('/v1/calls', {
      method: 'POST',
      body: JSON.stringify({ callAt })
    });
  }

  async uploadAudio(callId: string, fileUri: string, mimeType: string, durationSeconds: number): Promise<CallResponse> {
    const form = new FormData();
    form.append('file', {
      uri: fileUri,
      type: mimeType,
      name: `clip-${Date.now()}.m4a`
    } as any);
    form.append('durationSeconds', String(durationSeconds));

    return this.request(`/v1/calls/${callId}/audio`, {
      method: 'POST',
      body: form,
      isMultipart: true
    });
  }

  async getCall(callId: string): Promise<CallResponse> {
    return this.request(`/v1/calls/${callId}`, { method: 'GET' });
  }

  async updateDraft(callId: string, noteText: string): Promise<CallResponse> {
    return this.request(`/v1/calls/${callId}/draft`, {
      method: 'PATCH',
      body: JSON.stringify({ noteText })
    });
  }

  async finalizeCall(callId: string): Promise<CallResponse> {
    return this.request(`/v1/calls/${callId}/finalize`, { method: 'POST' });
  }

  async listCalls(): Promise<CallResponse[]> {
    return this.request('/v1/calls', { method: 'GET' });
  }

  private async request(path: string, options: { method: string; body?: BodyInit; isMultipart?: boolean }): Promise<any> {
    const accessToken = this.tokenHandlers.getAccessToken();
    if (!accessToken) {
      throw new Error('Missing access token');
    }

    const attempt = async (token: string) => {
      const headers: Record<string, string> = {
        Authorization: `Bearer ${token}`
      };
      if (!options.isMultipart) {
        headers['Content-Type'] = 'application/json';
      }

      return fetch(`${API_BASE_URL}${path}`, {
        method: options.method,
        headers,
        body: options.body
      });
    };

    let response: Response;
    try {
      response = await attempt(accessToken);
    } catch {
      throw new Error(`Cannot reach backend at ${API_BASE_URL}`);
    }
    const shouldRefresh = response.status === 401 || response.status === 403;
    if (!shouldRefresh) {
      if (!response.ok) {
        throw new Error(await extractErrorMessage(response));
      }
      return response.json();
    }

    const refreshTokenValue = this.tokenHandlers.getRefreshToken();
    if (!refreshTokenValue) {
      await this.tokenHandlers.onAuthFailure();
      throw new Error('Session expired');
    }

    try {
      const newTokens = await refreshTokens(refreshTokenValue);
      await this.tokenHandlers.onTokens(newTokens);
      response = await attempt(newTokens.accessToken);
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
          throw new Error('Session expired');
        }
        throw new Error(await extractErrorMessage(response));
      }
      return response.json();
    } catch (error) {
      await this.tokenHandlers.onAuthFailure();
      if (error instanceof Error && error.message === 'Session expired') {
        throw error;
      }
      throw new Error('Session expired');
    }
  }
}
