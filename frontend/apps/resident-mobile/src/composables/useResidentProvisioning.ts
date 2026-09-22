import { ref } from 'vue';
import type { Ref } from 'vue';

import { fetchQrProvisioning } from '../services/residentApi';
import { provisioningStorage } from '../services/secureStorage';
import type { QrProvisioning } from '../types';

export interface ResidentProvisioning {
  provisioning: Ref<QrProvisioning | null>;
  isRefreshing: Ref<boolean>;
  lastError: Ref<string | null>;
  restoreFromCache: () => Promise<QrProvisioning | null>;
  refresh: () => Promise<QrProvisioning | null>;
}

/**
 * Gerencia o segredo de assinatura do QR (subjectId/unitId/secretKey):
 * - Offline-first: restaura do SecureStorage/Vault local antes de qualquer rede.
 * - Quando online, re-provisiona via GET /api/v1/access/qr-token e re-escreve o cache.
 */
export function useResidentProvisioning(): ResidentProvisioning {
  const provisioning = ref<QrProvisioning | null>(null);
  const isRefreshing = ref(false);
  const lastError = ref<string | null>(null);

  async function restoreFromCache(): Promise<QrProvisioning | null> {
    const cached = await provisioningStorage.get();
    if (cached) {
      provisioning.value = { ...cached, cachedAt: cached.cachedAt ?? Date.now() };
    }
    return cached;
  }

  async function refresh(): Promise<QrProvisioning | null> {
    isRefreshing.value = true;
    lastError.value = null;
    try {
      const fetched = await fetchQrProvisioning();
      const fresh = { ...fetched, cachedAt: Date.now() };
      provisioning.value = fresh;
      await provisioningStorage.set(fresh);
      return fresh;
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Falha ao provisionar credenciais';
      lastError.value = message;
      // Mantém o cache: em offline o QR continua sendo gerado com o segredo local
      return provisioning.value;
    } finally {
      isRefreshing.value = false;
    }
  }

  return { provisioning, isRefreshing, lastError, restoreFromCache, refresh };
}