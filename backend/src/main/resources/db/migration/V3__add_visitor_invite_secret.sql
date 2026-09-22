-- backend/src/main/resources/db/migration/V3__add_visitor_invite_secret.sql
--
-- Adiciona o segredo HMAC por convite (secret_key). Cada convite de visitante
-- recebe um secret exclusivo de 32 bytes (hex de 64 chars) gerado no momento
-- da criação pelo VisitorInviteService. O segredo é usado pelo app do visitante
-- para assinar o payload do QR Code offline (mesmo mecanismo HMAC-SHA256 do
-- crypto-bridge), enquanto o backend valida o QR contra o secret_key do convite.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

ALTER TABLE visitor_invites ADD COLUMN IF NOT EXISTS secret_key VARCHAR(64);

-- Backfill seguro para convites já existentes (nunca deixa secret_key nula)
UPDATE visitor_invites SET secret_key = encode(gen_random_bytes(32), 'hex') WHERE secret_key IS NULL;

ALTER TABLE visitor_invites ALTER COLUMN secret_key SET NOT NULL;

CREATE INDEX idx_visitor_invites_resident ON visitor_invites(resident_id, status);