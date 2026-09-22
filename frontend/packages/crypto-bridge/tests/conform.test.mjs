import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { test } from 'node:test';

import { CryptoBridgeWeb } from '../dist/esm/web.js';

const bridge = new CryptoBridgeWeb();

const SUBJECT = '3f2504e0-4f89-41d3-9a0c-0305e82c3301';
const UNIT = '6ba7b810-9dad-11d1-80b4-00c04fd430c8';
const SECRET = 'condosafe-conformance-secret';
const FIXED_TMS = 1_750_000_000;

/**
 * Referência independente do backend (QrCodeCryptoService): HMAC-SHA256 hex
 * lowercase sobre "sub:unt:tms:jti" — mesma função que o Spring re-computa em
 * isValidSignature(). O bridge tem que produzir bytes idênticos.
 */
function referenceHmac(data, key) {
  return createHmac('sha256', key).update(data).digest('hex');
}

test('payload com HMAC idêntico ao node:crypto (mesmo algoritmo do backend)', async () => {
  const payload = await bridge.generateQrPayload({
    subjectId: SUBJECT,
    unitId: UNIT,
    secretKey: SECRET,
    timestamp: FIXED_TMS,
  });

  const expected = referenceHmac(`${SUBJECT}:${UNIT}:${FIXED_TMS}:${payload.jti}`, SECRET);
  assert.equal(payload.sig, expected);
  assert.equal(payload.tms, FIXED_TMS);
});

test('campos devolvidos re-computados como o backend (UUID canônico) batem com a assinatura', async () => {
  const payload = await bridge.generateQrPayload({ subjectId: SUBJECT, unitId: UNIT, secretKey: SECRET });

  // Espelha String.format("%s:%s:%d:%s", dto.sub(), dto.unt(), dto.tms(), dto.jti()) do QrCodeCryptoService
  const recomputed = referenceHmac(`${payload.sub}:${payload.unt}:${payload.tms}:${payload.jti}`, SECRET);
  assert.equal(recomputed, payload.sig);
});

test('normaliza UUIDs para lowercase canônico (evita INVALID_SIGNATURE)', async () => {
  const payload = await bridge.generateQrPayload({
    subjectId: SUBJECT.toUpperCase(),
    unitId: UNIT.toUpperCase(),
    secretKey: SECRET,
    timestamp: FIXED_TMS,
  });

  assert.equal(payload.sub, SUBJECT);
  assert.equal(payload.unt, UNIT);
  assert.equal(
    payload.sig,
    referenceHmac(`${SUBJECT}:${UNIT}:${FIXED_TMS}:${payload.jti}`, SECRET),
  );
});

test('rawJson é JSON válido com exatamente as 5 chaves do QrCodeDTO', async () => {
  const payload = await bridge.generateQrPayload({ subjectId: SUBJECT, unitId: UNIT, secretKey: SECRET });

  const parsed = JSON.parse(payload.rawJson);
  assert.deepEqual(Object.keys(parsed).sort(), ['jti', 'sig', 'sub', 'tms', 'unt']);
  assert.equal(parsed.sub, payload.sub);
  assert.equal(parsed.unt, payload.unt);
  assert.equal(parsed.tms, payload.tms);
  assert.equal(parsed.jti, payload.jti);
  assert.equal(parsed.sig, payload.sig);
});

test('jti é um UUID único a cada geração', async () => {
  const a = await bridge.generateQrPayload({ subjectId: SUBJECT, unitId: UNIT, secretKey: SECRET });
  const b = await bridge.generateQrPayload({ subjectId: SUBJECT, unitId: UNIT, secretKey: SECRET });

  assert.notEqual(a.jti, b.jti);
  assert.match(a.jti, /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
});

test('rejeita UUID inválido com erro claro', async () => {
  await assert.rejects(
    bridge.generateQrPayload({ subjectId: 'not-a-uuid', unitId: UNIT, secretKey: SECRET }),
    /não é um UUID válido/,
  );
});

test('tms padrão é epoch em segundos (dentro de Date.now()/1000)', async () => {
  const before = Math.floor(Date.now() / 1000);
  const payload = await bridge.generateQrPayload({ subjectId: SUBJECT, unitId: UNIT, secretKey: SECRET });
  const after = Math.floor(Date.now() / 1000);

  assert.ok(payload.tms >= before && payload.tms <= after);
});