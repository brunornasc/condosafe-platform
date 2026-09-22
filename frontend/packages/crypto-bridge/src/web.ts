import { WebPlugin } from '@capacitor/core';
import type { CryptoBridgePlugin, GenerateQrPayloadOptions, GeneratedQrPayload } from './definitions';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * Normaliza o identificador para o formato canônico (lowercase) que o backend
 * usa ao recomputar o HMAC (String.format("%s", UUID.toString())). Assinar o
 * input cru geraria INVALID_SIGNATURE para UUIDs em maiúsculas/não-canônicos.
 */
function toCanonicalUuid(value: string, field: string): string {
    if (!UUID_PATTERN.test(value)) {
        throw new Error(`CryptoBridge: '${field}' não é um UUID válido: "${value}"`);
    }
    return value.toLowerCase();
}

/** UUID v4 com fallback para WebViews/contextos onde crypto.randomUUID não existe. */
function randomUuid(): string {
    if (typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export class CryptoBridgeWeb extends WebPlugin implements CryptoBridgePlugin {
    async generateQrPayload(options: GenerateQrPayloadOptions): Promise<GeneratedQrPayload> {
        if (!crypto.subtle) {
            throw new Error(
                'CryptoBridge: Web Crypto API (crypto.subtle) indisponível. Use HTTPS ou a implementação nativa.',
            );
        }

        const subjectId = toCanonicalUuid(options.subjectId, 'subjectId');
        const unitId = toCanonicalUuid(options.unitId, 'unitId');
        const tms = options.timestamp ?? Math.floor(Date.now() / 1000);
        const jti = randomUuid();
        const rawData = `${subjectId}:${unitId}:${tms}:${jti}`;

        // Cálculo Web Crypto API do HMAC-SHA256
        const enc = new TextEncoder();
        const keyData = enc.encode(options.secretKey);
        const cryptoKey = await crypto.subtle.importKey(
            'raw',
            keyData,
            { name: 'HMAC', hash: 'SHA-256' },
            false,
            ['sign'],
        );

        const signatureBuffer = await crypto.subtle.sign(
            'HMAC',
            cryptoKey,
            enc.encode(rawData),
        );

        const sig = Array.from(new Uint8Array(signatureBuffer))
            .map((b) => b.toString(16).padStart(2, '0'))
            .join('');

        const payload = {
            sub: subjectId,
            unt: unitId,
            tms,
            jti,
            sig,
        };

        return {
            ...payload,
            rawJson: JSON.stringify(payload),
        };
    }
}