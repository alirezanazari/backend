package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.gateway.web.GatewayResponseWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AppDecryptionWebFilterTest {
    private static final byte[] KEY_INFO =
            "red-bank-gateway-app-encryption-v1".getBytes(StandardCharsets.UTF_8);

    @Test
    void decryptsBodyAndRemovesEncryptionHeadersBeforeForwarding() throws Exception {
        AppEncryptionProperties properties = new AppEncryptionProperties(true, 300, 1_048_576);
        KeyExchangeService service = new KeyExchangeService(properties, Clock.systemUTC());
        KeyPair client = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyExchangeResponse response = service.create(new KeyExchangeRequest(
                Base64.getEncoder().encodeToString(client.getPublic().getEncoded())
        ));
        byte[] nonce = Base64.getDecoder().decode("AAECAwQFBgcICQoL");
        byte[] plaintext = "{\"context\":{},\"data\":{\"username\":\"user\"}}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = encrypt(response, client, nonce, plaintext);
        AtomicReference<String> forwardedBody = new AtomicReference<>();
        AtomicReference<HttpHeaders> forwardedHeaders = new AtomicReference<>();
        AppDecryptionWebFilter filter = new AppDecryptionWebFilter(
                service,
                properties,
                new GatewayResponseWriter(new ObjectMapper())
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/iam/login")
                        .header(AppDecryptionWebFilter.KEY_EXCHANGE_ID_HEADER, response.keyExchangeId())
                        .header(AppDecryptionWebFilter.NONCE_HEADER, Base64.getEncoder().encodeToString(nonce))
                        .body(Base64.getEncoder().encodeToString(ciphertext))
        );

        StepVerifier.create(filter.filter(exchange, current ->
                DataBufferUtils.join(current.getRequest().getBody()).flatMap(buffer -> {
                    byte[] body = new byte[buffer.readableByteCount()];
                    buffer.read(body);
                    DataBufferUtils.release(buffer);
                    forwardedBody.set(new String(body, StandardCharsets.UTF_8));
                    forwardedHeaders.set(current.getRequest().getHeaders());
                    return Mono.empty();
                })
        )).verifyComplete();

        assertThat(forwardedBody.get()).isEqualTo(new String(plaintext, StandardCharsets.UTF_8));
        assertThat(forwardedHeaders.get().containsKey(AppDecryptionWebFilter.KEY_EXCHANGE_ID_HEADER))
                .isFalse();
        assertThat(forwardedHeaders.get().containsKey(AppDecryptionWebFilter.NONCE_HEADER)).isFalse();
    }

    private byte[] encrypt(
            KeyExchangeResponse response,
            KeyPair client,
            byte[] nonce,
            byte[] plaintext
    ) throws Exception {
        PublicKey serverPublicKey = KeyFactory.getInstance("X25519").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(response.serverPublicKey()))
        );
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(client.getPrivate());
        agreement.doPhase(serverPublicKey, true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] pseudoRandomKey = hmac(
                response.keyExchangeId().getBytes(StandardCharsets.UTF_8),
                sharedSecret
        );
        byte[] key = hmac(pseudoRandomKey, concat(KEY_INFO, new byte[]{1}));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(response.keyExchangeId().getBytes(StandardCharsets.UTF_8));
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
