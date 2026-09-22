import { Capacitor } from '@capacitor/core';
import type { QrProvisioning } from '../types';

const KEY_PREFIX = 'condosafe.';

const AUTH_TOKEN_KEY = `${KEY_PREFIX}auth.token`;
const PROVISIONING_KEY = `${KEY_PREFIX}qr.provisioning`;

/**
 * Camada de armazenamento com foco offline-first:
 * - Em plataforma nativa (Android/iOS) usa o @aparajita/capacitor-secure-storage
 *   (KeyStore/Keychain) para manter o secret_key protegido fora do armazenamento comum.
 * - No navegador (PWA/dev) o plugin nativo não está disponível, então cai para
 *   @capacitor/preferences (fallback).
 *
 * Valor é sempre serializado como JSON string para unificar a leitura nas duas
 * camadas (SecureStorage.get auto-parsea; aqui normalizamos via JSON.parse).
 */
const secureBackend = {

  async isAvailable(): Promise<boolean> {
    if (!Capacitor.isNativePlatform()) {
      return false;
    }
    try {
      const { SecureStorage } = await import('@aparajita/capacitor-secure-storage');
      await SecureStorage.setKeyPrefix('condosafe_');
      return true;
    } catch {
      return false;
    }
  },

  async get<T>(key: string): Promise<T | null> {
    const { SecureStorage } = await import('@aparajita/capacitor-secure-storage');
    const raw = await SecureStorage.get(key);
    if (raw == null) {
      return null;
    }
    return typeof raw === 'string' ? (JSON.parse(raw) as T) : (raw as T);
  },

  async set(key: string, value: unknown): Promise<void> {
    const { SecureStorage } = await import('@aparajita/capacitor-secure-storage');
    await SecureStorage.set(key, JSON.stringify(value));
  },

  async remove(key: string): Promise<void> {
    const { SecureStorage } = await import('@aparajita/capacitor-secure-storage');
    await SecureStorage.remove(key);
  },
};

const preferencesBackend = {
  async isAvailable(): Promise<boolean> {
    return true;
  },

  async get<T>(key: string): Promise<T | null> {
    const { Preferences } = await import('@capacitor/preferences');
    const { value } = await Preferences.get({ key });
    return value == null ? null : (JSON.parse(value) as T);
  },

  async set(key: string, value: unknown): Promise<void> {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.set({ key, value: JSON.stringify(value) });
  },

  async remove(key: string): Promise<void> {
    const { Preferences } = await import('@capacitor/preferences');
    await Preferences.remove({ key });
  },
};

type StorageBackend = typeof secureBackend;

let activeBackend: StorageBackend | null = null;

async function resolveBackend(): Promise<StorageBackend> {
  if (!activeBackend) {
    activeBackend = (await secureBackend.isAvailable()) ? secureBackend : preferencesBackend;
  }
  return activeBackend;
}

export const storage = {
  async get<T>(key: string): Promise<T | null> {
    const backend = await resolveBackend();
    return backend.get<T>(key);
  },

  async set(key: string, value: unknown): Promise<void> {
    const backend = await resolveBackend();
    await backend.set(key, value);
  },

  async remove(key: string): Promise<void> {
    const backend = await resolveBackend();
    await backend.remove(key);
  },
};

export const authStorage = {
  getToken(): Promise<string | null> {
    return storage.get<string>(AUTH_TOKEN_KEY);
  },
  setToken(token: string): Promise<void> {
    return storage.set(AUTH_TOKEN_KEY, token);
  },
  clear(): Promise<void> {
    return storage.remove(AUTH_TOKEN_KEY);
  },
};

export const provisioningStorage = {
  get(): Promise<QrProvisioning | null> {
    return storage.get<QrProvisioning>(PROVISIONING_KEY);
  },
  set(provisioning: QrProvisioning): Promise<void> {
    return storage.set(PROVISIONING_KEY, provisioning);
  },
  clear(): Promise<void> {
    return storage.remove(PROVISIONING_KEY);
  },
};