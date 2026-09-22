import type { QrProvisioning, ResidentProfile } from '../types';
import { authStorage } from './secureStorage';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function fetchJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await authStorage.getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };

  const response = await fetch(`${BASE_URL}${path}`, { ...init, headers });

  if (!response.ok) {
    throw new ApiError(`Falha na requisição ${path} (HTTP ${response.status})`, response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Perfil devolvido pelo backend em /api/v1/me (Keycloak subject etc.) */
export function fetchResidentProfile(): Promise<ResidentProfile> {
  return fetchJson<ResidentProfile>('/me');
}

/*
 * Provisiona o segredo de assinatura do QR para o dispositivo do morador.
 * Em produção, rota proteger por rol RESIDENT/ADMIN (SecurityConfig) e o
 * segredo é armazenado no SecureStorage/Vault antes de ser usado offline.
 * O backend expõe GET /api/v1/access/qr-token com { userId, unitId, secretKey }.
 */
export async function fetchQrProvisioning(): Promise<QrProvisioning> {
  return fetchJson<QrProvisioning>('/access/qr-token');
}