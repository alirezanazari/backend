package com.redbank.gateway.security;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "redbank.gateway.app-encryption.enabled", havingValue = "true")
public class KeyExchangeService {
    private final AppEncryptionProperties properties;
    private final Clock clock;
    private final Map<String, GatewayKeyExchangeSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public KeyExchangeService(AppEncryptionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    KeyExchangeService(AppEncryptionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public KeyExchangeResponse create(KeyExchangeRequest request) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");
            KeyPair serverKeyPair = generator.generateKeyPair();
            Instant expiresAt = clock.instant().plusSeconds(properties.keyExchangeTtlSeconds());
            String sessionId = UUID.randomUUID().toString();

            sessions.put(sessionId, new GatewayKeyExchangeSession(
                    sessionId,
                    request.clientPublicKey(),
                    serverKeyPair,
                    expiresAt
            ));

            String publicKey = Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
            return new KeyExchangeResponse(sessionId, publicKey, expiresAt);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create gateway key exchange session", exception);
        }
    }

    record GatewayKeyExchangeSession(
            String id,
            String clientPublicKey,
            KeyPair serverKeyPair,
            Instant expiresAt
    ) {
    }
}
