package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class KeyExchangeServiceTest {
    private static final byte[] KEY_INFO =
            "red-bank-gateway-app-encryption-v1".getBytes(StandardCharsets.UTF_8);

    @Test
    void derivesSharedKeyAndDecryptsAesGcmPayload() throws Exception {
        KeyPair client = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyExchangeService service = new KeyExchangeService(
                new AppEncryptionProperties(true, 300, 1_048_576),
                Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneOffset.UTC)
        );
        KeyExchangeResponse response = service.create(new KeyExchangeRequest(
                Base64.getEncoder().encodeToString(client.getPublic().getEncoded())
        ));

        PublicKey serverPublicKey = KeyFactory.getInstance("X25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(response.serverPublicKey()))
        );
        byte[] key = deriveClientKey(response.keyExchangeId(), client, serverPublicKey);
        byte[] nonce = Base64.getDecoder().decode("AAECAwQFBgcICQoL");
        byte[] plaintext = "{\"context\":{},\"data\":{\"password\":\"secret\"}}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = encrypt(response.keyExchangeId(), key, nonce, plaintext);

        assertThat(service.decrypt(response.keyExchangeId(), nonce, ciphertext))
                .isEqualTo(plaintext);
    }

    @Test
    void rejectsMalformedClientPublicKey() {
        KeyExchangeService service = new KeyExchangeService(
                new AppEncryptionProperties(true, 300, 1_048_576),
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.create(new KeyExchangeRequest("not-base64")))
                .isInstanceOf(AppEncryptionException.class);
    }

    private byte[] deriveClientKey(String sessionId, KeyPair client, PublicKey serverPublicKey)
            throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(client.getPrivate());
        agreement.doPhase(serverPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] pseudoRandomKey = hmac(sessionId.getBytes(StandardCharsets.UTF_8), sharedSecret);
        return hmac(pseudoRandomKey, concat(KEY_INFO, new byte[]{1}));
    }

    private byte[] encrypt(String sessionId, byte[] key, byte[] nonce, byte[] plaintext)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(sessionId.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(plaintext);
    }

    private byte[] hmac(byte[] key, byte[] value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value);
    }

    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
