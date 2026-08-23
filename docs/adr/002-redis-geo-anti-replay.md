# ADR 002: Mitigação de Ataques de Replay e Rastreamento de Geofence com Redis em Memória

* **Status:** Aceito
* **Data:** 2026-08-23
* **Decisores:** Bruno Rodrigues
* **Módulos Afetados:** `backend/access`, `backend/emergency`, `infrastructure`

---

## 1. Contexto e Problema

O **CondoSafe Platform** precisa garantir integridade de segurança física e suporte a operações em tempo real com baixíssima latência:

1. **Prevenção de Ataques de Replay (Replay Attack):** Como os QR Codes de acesso gerados nos dispositivos móveis são baseados em TOTP/HMAC com janela de validade de 30 segundos, um invasor ou visitante mal-intencionado poderia gravar a tela ou tirar um print e reutilizar o mesmo QR Code dentro dessa janela de tolerância.
2. **Latência Crítica na Catraca:** A validação de uso único precisa ocorrer em tempo de execução com latência sub-milissegundo (< 2ms). Fazer lock pessimista ou checagem de duplicidade com `SELECT FOR UPDATE` no PostgreSQL geraria contenção de disco e gargalo de concorrência.
3. **Triagem de Evacuação em Emergência:** Durante desastres, a plataforma precisa calcular distâncias de centenas de moradores em relação às zonas de segurança sem onerar o banco espacial com consultas geoespaciais repetitivas a cada segundo.

---

## 2. Decisão

Adotar o **Redis 7+ em memória (via driver reativo Lettuce / `ReactiveStringRedisTemplate`)** como mecanismo de controle de concorrência atômica e computação geoespacial efêmera.

### 2.1. Bloqueio Anti-Replay com Primitivas Atômicas

Para cada tentativa de validação de acesso, o backend executa a operação atômica:

```redis
SET replay:pass:<jti> "USED" NX EX 35
```

* **`jti` (JWT ID / Nonce Único):** Identificador exclusivo presente no payload assinado do QR Code.
* **`NX` (Not Exists):** Garante atomicidade em nível de thread única do Redis (*single-threaded event loop*). Se a chave já existir, a operação falha imediatamente sem condição de corrida (*race condition*).
* **`EX 35`:** Expiração automática de 35 segundos (tempo do slot de 30s + 5s de margem para compensar desvio de relógio), liberando a memória RAM automaticamente sem necessidade de rotinas de limpeza (*garbage collection*).

### 2.2. Agregação Espacial de Emergência (`GEOSEARCH` / `GEOADD`)

Para telemetria durante evacuações:
* Os pings de localização são armazenados no Redis via `GEOADD emergency:telemetry <longitude> <latitude> <userId>`.
* As zonas seguras são avaliadas em tempo real com `GEOSEARCH emergency:telemetry FROMLONLAT <safe_zone_lon> <safe_zone_lat> BYRADIUS <radius_meters> M`, descarregando as consultas em tempo real do PostGIS.

---

## 3. Alternativas Consideradas

### Alternativa A: Tabela de Nonces no PostgreSQL
* **Prós:** Menos uma dependência de infraestrutura em memória.
* **Contras:** A cada leitura de catraca seria necessário um `INSERT ... ON CONFLICT DO NOTHING`, gerando escrita contínua em log WAL, fragmentação de índices B-Tree e lentidão sob concorrência em horários de pico.

### Alternativa B: Cache Local em Memória da JVM (Caffeine / Guava Cache)
* **Prós:** Latência de leitura na ordem de nanossegundos.
* **Contras:** Não funciona em ambientes distribuídos. Se o backend escalar horizontalmente para 2 ou mais instâncias atrás de um Load Balancer, uma réplica não saberia que o `jti` já foi utilizado na outra.

---

## 4. Consequências

### Positivas
* **Proteção Criptográfica Completa:** Garante que qualquer QR Code interceptado só possa ser utilizado uma única vez, neutralizando ataques de replay dentro da janela de 30s.
* **Execução em Sub-Milissegundo:** Comandos `SET NX` executam inteiramente em memória RAM, viabilizando acionamento mecânico da catraca em < 10ms.
* **Consistência em Ambientes Multi-Instância:** Múltiplas réplicas da API compartilham o mesmo estado atômico de nonces.

### Negativas / Custos Aceitos
* **Volatilidade de Memória:** O Redis é mantido como armazenamento efêmero; os logs permanentes continuam delegados ao Apache Kafka e persistidos de forma assíncrona no PostgreSQL.
* **Monitoramento de Memória (Maxmemory Policy):** Necessidade de dimensionar TTL curto (35s) para evitar saturação de memória RAM do Redis.

---

## 5. Referências

* Redis Command Reference: `SET` (NX / EX options), `GEOSEARCH`.
* NIST Special Publication 800-63B: *Digital Identity Guidelines (Replay Resistance)*.
* RFC 6238: *TOTP: Time-Based One-Time Password Algorithm*.