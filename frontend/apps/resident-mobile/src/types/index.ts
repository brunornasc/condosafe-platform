import type { GeneratedQrPayload } from '@condosafe/crypto-bridge';

export type { GeneratedQrPayload } from '@condosafe/crypto-bridge';

export type QrStatus = 'loading' | 'ready' | 'offline-cached' | 'error';

export interface QrProvisioning {
  /** Id interno do morador na tabela `users` (trafega como `sub` no QR) */
  userId: string;
  /** Id da unidade vinculada (traga como `unt` no QR) */
  unitId: string;
  /** Segredo compartilhado armazenado no SecureStorage/Vault local */
  secretKey: string;
  /** Unix epoch em segundos do último refresh (para expiração do cache) */
  cachedAt?: number;
}

export interface ResidentProfile {
  subject: string;
  preferredUsername: string;
  email: string;
  roles: string[] | string;
}

export interface GenerateQrResult {
  /** Payload assinado pelo crypto-bridge */
  payload: GeneratedQrPayload;
  /** Epoch do slot TOTP de 30s em que o payload foi gerado */
  slot: number;
}