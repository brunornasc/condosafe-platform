import { registerPlugin } from '@capacitor/core';
import type { CryptoBridgePlugin } from './definitions';

const CryptoBridge = registerPlugin<CryptoBridgePlugin>('CryptoBridge', {
    web: () => import('./web').then(m => new m.CryptoBridgeWeb()),
});

export * from './definitions';
export { CryptoBridge };