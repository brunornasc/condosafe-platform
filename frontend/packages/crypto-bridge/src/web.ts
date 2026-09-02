import { WebPlugin } from '@capacitor/core';
import type { CryptoBridgePlugin, GenerateQrPayloadOptions, GeneratedQrPayload } from './definitions';

export class CryptoBridgeWeb extends WebPlugin implements CryptoBridgePlugin {
    async generateQrPayload(options: GenerateQrPayloadOptions): Promise<GeneratedQrPayload> {
        const tms = options.timestamp ?? Math.floor(Date.now() / 1000);
        const jti = crypto.randomUUID();
        const rawData = `${options.subjectId}:${options.unitId}:${tms}:${jti}`;

        // Cálculo Web Crypto API do HMAC-SHA256
        const enc = new TextEncoder();
        const keyData = enc.encode(options.secretKey);
        const cryptoKey = await crypto.subtle.importKey(
            'raw',
            keyData,
            { name: 'HMAC', hash: 'SHA-256' },
            false,
            ['sign']
        );

        const signatureBuffer = await crypto.subtle.sign(
            'HMAC',
            cryptoKey,
            enc.encode(rawData)
        );

        const sig = Array.from(new Uint8Array(signatureBuffer))
            .map(b => b.toString(16).padStart(2, '0'))
            .join('');

        const payload = {
            sub: options.subjectId,
            unt: options.unitId,
            tms,
            jti,
            sig
        };

        return {
            ...payload,
            rawJson: JSON.stringify(payload)
        };
    }
}