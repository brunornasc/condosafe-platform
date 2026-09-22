import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.condosafe.resident',
  appName: 'CondoSafe Residente',
  webDir: 'dist',
  plugins: {
    CryptoBridge: {},
    Preferences: {},
    SecureStorage: {
      // Acessa o KeyStore/Keychain mantendo o segredo fora do armazenamento comum
      keychainAccess: 'always',
    },
  },
};

export default config;