# ADR 001: Desacoplamento e Ingestão de Eventos em Alta Escala com Apache Kafka

* **Status:** Aceito
* **Data:** 2026-08-19
* **Decisores:** Bruno Rodrigues
* **Módulos Afetados:** `backend/access`, `backend/emergency`, `backend/notification`, `edge`

---

## 1. Contexto e Problema

O **CondoSafe Platform** atua em dois cenários operacionais distintos:

1. **Rotina (Acesso Físico Contínuo):** Centenas de catracas e cancelas validam acessos simultaneamente em horários de pico. Cada passagem precisa ser auditada com garantia de entrega e ordenação por unidade, sem impactar a latência do acionamento físico do relé (< 10ms).
2. **Emergência (Evacuação em Desastre):** Ao acionar uma evacuação geral (ex: alarme de incêndio), milhares de dispositivos móveis passam a transmitir coordenadas de GPS a cada 2 a 5 segundos (`POST /api/v1/emergency/telemetry/ping`).

### Problemas da Abordagem Síncrona / Monolítica
* **Saturação do Pool de Conexões (Connection Starvation):** Gravar cada log de passagem e ping de telemetria diretamente no PostgreSQL causaria esgotamento imediato do pool JDBC/R2DBC e contenção de I/O em disco.
* **Acoplamento Temporal:** Falhas temporárias ou lentidão no banco relacional degradariam a resposta dos endpoints de validação física da catraca.
* **Dificuldade de Fan-out:** Notificar múltiplos consumidores (auditoria no Postgres, broadcast SSE para a portaria e pipeline de push notifications) via chamadas HTTP síncronas adiciona latência cumulativa e pontos únicos de falha.

---

## 2. Decisão

Adotar o **Apache Kafka 3.7+ em modo KRaft (Kafka Raft Consensus)** como espinha dorsal de streaming de eventos e ingestão assíncrona da plataforma.

### Topologia de Tópicos e Estratégia de Particionamento

| Tópico | Partições | Chave de Particionamento (`Key`) | Justificativa Técnica |
| :--- | :--- | :--- | :--- |
| `access-events` | 3 | `unitId` | Garante a ordenação estrita dos eventos de acesso por apartamento/unidade dentro da mesma partição. |
| `evacuation-telemetry` | 6 | `userId` | Distribui a carga de centenas de pings de GPS simultâneos entre múltiplos workers, mantendo o histórico de rota do morador na mesma partição. |
| `incident-events` | 1 | `condominiumId` | Baixo volume e altíssima criticidade. Uma partição única garante ordem total de alertas para broadcast via SSE. |

### Configurações de Confiabilidade e Resiliência
* **Produtores (Spring Boot / Edge C++):**
    * `acks=all`: Confirmação apenas após a gravação no líder e em todas as réplicas sincronizadas (ISR).
    * `enable.idempotence=true`: Previne duplicação de mensagens em caso de retentativas na camada de rede.
* **Consumidores Reativos (`reactor-kafka`):**
    * Persistência em lote (*batch processing*) no PostgreSQL a cada 500ms ou 100 registros.
    * Consumidores desacoplados para envio de Server-Sent Events (SSE) ao dashboard da portaria.

---

## 3. Alternativas Consideradas

### Alternativa A: RabbitMQ / AMQP
* **Prós:** Roteamento flexível via exchanges (Topic/Direct), menor complexidade inicial.
* **Contras:** Modelo baseado em filas transitórias (*smart broker, dumb consumer*), onde as mensagens são deletadas após consumo. Dificuldade de *replay* de eventos históricos para perícias e menor vazão (*throughput*) para telemetria em massa comparado ao log append-only do Kafka.

### Alternativa B: Redis Streams / Pub-Sub
* **Prós:** Já presente na stack (Redis 7).
* **Contras:** O Pub/Sub tradicional do Redis não possui persistência ou confirmação de entrega (*fire-and-forget*). O Redis Streams suporta grupos de consumidores, mas compete por memória RAM com as operações de Geofencing (`GEOADD`) e anti-replay (`SET NX`).

---

## 4. Consequências

### Positivas
* **Absorção de Rajadas de Tráfego (Backpressure & Buffering):** Durante evacuações, o Kafka armazena com segurança milhões de pings de telemetria em disco sem derrubar a API ou travar o PostgreSQL.
* **Latência de Escrita Previsível:** O endpoint REST/WebFlux devolve `HTTP 202 Accepted` em < 5ms logo após o `kafkaTemplate.send()` ser enfileirado no buffer do produtor.
* **Auditabilidade e Reprodutibilidade:** Capacidade de reprocessar eventos do início do tópico (*offset reset to earliest*) para auditorias forenses de incidentes e relatórios para o corpo de bombeiros.

### Negativas / Custos Aceitos
* **Consistência Eventual:** Há um atraso (*lag*) de alguns milissegundos entre a passagem na catraca e sua persistência final na tabela `access_logs`.
* **Complexidade Operacional:** Necessidade de monitoramento de *consumer lag*, retenção de logs em disco e alocação de memória JVM para o broker.

---

## 5. Referências

* Apache Kafka Documentation: *KRaft Mode (KIP-500)*.
* Martin Kleppmann: *Designing Data-Intensive Applications* (Capítulo 11: Stream Processing).
* Gregor Hohpe, Bobby Woolf: *Enterprise Integration Patterns* (Publish-Subscribe Channel).