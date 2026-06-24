package com.redbank.kyc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.kyc.config.DebugProperties;
import com.redbank.kyc.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-24T09:00:00Z");
    private static final String SHARED_HMAC_SECRET = "test-jwt-signing-secret-change-me-32bytes";

    @Test
    void asymmetricVerifierRejectsAdminTokenSignedWithVerifierSideHmacSecret() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtTokenService service = service(publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        String forgedAdminToken = hmacToken(List.of("ADMIN"));

        assertThatThrownBy(() -> service.verifyAdminToken("Bearer " + forgedAdminToken))
                .isInstanceOf(KycUnauthorizedException.class);
    }

    @Test
    void asymmetricVerifierAcceptsAdminTokenSignedByIamPrivateKey() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtTokenService service = service(publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        String adminToken = rsaToken((RSAPrivateKey) keyPair.getPrivate(), List.of("ADMIN"));

        VerifiedToken verifiedToken = service.verifyAdminToken("Bearer " + adminToken);

        assertThat(verifiedToken.hasRole("ADMIN")).isTrue();
    }

    @Test
    void referralTokenRequiresOnboardingReferralScopeEvenWhenUserRoleIsPresent() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtTokenService service = service(publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        String userToken = rsaToken((RSAPrivateKey) keyPair.getPrivate(), List.of("USER"), List.of());

        assertThatThrownBy(() -> service.verifyReferralToken("Bearer " + userToken))
                .isInstanceOf(KycUnauthorizedException.class);
    }

    @Test
    void referralTokenAcceptsOnboardingReferralScope() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtTokenService service = service(publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        String referralToken = rsaToken(
                (RSAPrivateKey) keyPair.getPrivate(),
                List.of("ONBOARDING"),
                List.of("ONBOARDING_REFERRAL_SET")
        );

        VerifiedToken verifiedToken = service.verifyReferralToken("Bearer " + referralToken);

        assertThat(verifiedToken.hasScope("ONBOARDING_REFERRAL_SET")).isTrue();
    }

    private JwtTokenService service(String publicKeyPem) {
        return new JwtTokenService(
                new JwtProperties("red-bank", "red-bank-api", SHARED_HMAC_SECRET, publicKeyPem),
                new DebugProperties(false, "debug-jwt-secret-with-enough-entropy"),
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String hmacToken(List<String> roles) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(roles, List.of()));
        jwt.sign(new MACSigner(SHARED_HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String rsaToken(RSAPrivateKey privateKey, List<String> roles) throws JOSEException {
        return rsaToken(privateKey, roles, List.of());
    }

    private String rsaToken(RSAPrivateKey privateKey, List<String> roles, List<String> scopes) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims(roles, scopes));
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(List<String> roles, List<String> scopes) {
        return new JWTClaimsSet.Builder()
                .issuer("red-bank")
                .audience("red-bank-api")
                .subject(UUID.randomUUID().toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900)))
                .claim("profile_id", UUID.randomUUID().toString())
                .claim("device_id", UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("scope", scopes)
                .build();
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String publicKeyPem(RSAPublicKey publicKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
