package com.condosafe.access.infrastructure.services;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class AntiReplayService {

    private static final String KEY_PREFIX = "replay:pass:";
    private static final Duration TTL = Duration.ofSeconds(35);

    private final ReactiveStringRedisTemplate redisTemplate;

    public AntiReplayService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Executa um comando atômico SET replay:pass:<jti> "USED" NX EX 35.
     * Retorna true se a chave foi inserida com sucesso (passe nunca usado).
     * Retorna false se a chave já existia (tentativa de replay).
     */
    public Mono<Boolean> acquireLock(String jti) {
        String key = KEY_PREFIX + jti;
        return redisTemplate.opsForValue()
                .setIfAbsent(key, "USED", TTL);
    }
}