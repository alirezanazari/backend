package com.redbank.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "redbank.gateway.app-encryption.enabled", havingValue = "true")
public class KeyExchangeService {
    private static final String KEY_AGREEMENT = "X25519";
    private static final String KEY_DERIVATION = "HmacSHA256";
    private static final byte[] KEY_INFO =
            "red-bank-gateway-app-encryption-v1".getBytes(StandardCharsets.UTF_8);

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
            Instant now = clock.instant();
            sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));

            String sessionId = UUID.randomUUID().toString();
            PublicKey clientPublicKey = parseClientPublicKey(request.clientPublicKey());
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_AGREEMENT);
            KeyPair serverKeyPair = generator.generateKeyPair();
            SecretKey encryptionKey = deriveEncryptionKey(sessionId, serverKeyPair, clientPublicKey);
            Instant expiresAt = now.plusSeconds(properties.keyExchangeTtlSeconds());

            sessions.put(sessionId, new GatewayKeyExchangeSession(
                    encryptionKey,
                    expiresAt
            ));

            String publicKey = Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded());
            return new KeyExchangeResponse(sessionId, publicKey, expiresAt);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create gateway key exchange session", exception);
        }
    }

    public byte[] decrypt(String sessionId, byte[] nonce, byte[] ciphertext) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new AppEncryptionException("Missing key exchange session");
        }
        if (nonce == null || nonce.length != 12) {
            throw new AppEncryptionException("Encryption nonce must be 12 bytes");
        }

        GatewayKeyExchangeSession session = sessions.get(sessionId);
        if (session == null) {
            throw new AppEncryptionException("Unknown key exchange session");
        }
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(sessionId, session);
            throw new AppEncryptionException("Key exchange session has expired");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, session.encryptionKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(sessionId.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new AppEncryptionException("Unable to decrypt request payload", exception);
        }
    }

    private PublicKey parseClientPublicKey(String encodedKey) throws GeneralSecurityException {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            return KeyFactory.getInstance(KEY_AGREEMENT)
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException exception) {
            throw new AppEncryptionException("Client public key is not valid Base64", exception);
        }
    }

    private SecretKey deriveEncryptionKey(
            String sessionId,
            KeyPair serverKeyPair,
            PublicKey clientPublicKey
    ) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance(KEY_AGREEMENT);
        agreement.init(serverKeyPair.getPrivate());
        agreement.doPhase(clientPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] salt = sessionId.getBytes(StandardCharsets.UTF_8);
        byte[] pseudoRandomKey = hmac(salt, sharedSecret);
        byte[] output = hmac(pseudoRandomKey, concat(KEY_INFO, new byte[]{1}));
        Arrays.fill(sharedSecret, (byte) 0);
        Arrays.fill(pseudoRandomKey, (byte) 0);
        SecretKey encryptionKey = new SecretKeySpec(output, 0, 32, "AES");
        Arrays.fill(output, (byte) 0);
        return encryptionKey;
    }

    private byte[] hmac(byte[] key, byte[] value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(KEY_DERIVATION);
        mac.init(new SecretKeySpec(key, KEY_DERIVATION));
        return mac.doFinal(value);
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    record GatewayKeyExchangeSession(
            SecretKey encryptionKey,
            Instant expiresAt
    ) {
    }
}
