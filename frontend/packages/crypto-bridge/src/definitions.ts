export interface GenerateQrPayloadOptions {
    /**
     * Identificador do usuário (morador) ou convite (visitante)
     */
    subjectId: string;

    /**
     * Identificador da unidade vinculada
     */
    unitId: string;

    /**
     * Chave secreta compartilhada em formato texto (KeyStore / Vault local)
     */
    secretKey: string;

    /**
     * Timestamp customizado em segundos (opcional, padrão = Instant.now().epochSecond)
     */
    timestamp?: number;
}

export interface GeneratedQrPayload {
    /**
     * Payload formatado pronto para ser convertido em imagem de QR Code
     */
    sub: string;
    unt: string;
    tms: number;
    jti: string;
    sig: string;
    /**
     * String serializada em JSON para desenhar no Canvas/SVG
     */
    rawJson: string;
}

export interface CryptoBridgePlugin {
    /**
     * Gera um payload de QR Code assinado localmente com HMAC-SHA256 e UUID v4
     */
    generateQrPayload(options: GenerateQrPayloadOptions): Promise<GeneratedQrPayload>;
}