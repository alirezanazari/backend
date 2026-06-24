package com.redbank.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.gateway.config.DebugProperties;
import com.redbank.gateway.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class GatewayJwtServiceTest {
    private static final String SIGNING_SECRET = "gateway-test-signing-secret-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-06-13T10:00:00Z");

    @Test
    void verifiesExpectedClaims() {
        GatewayJwtService service = service("develop", false);

        GatewayToken token = service.verifyBearerToken("Bearer " + token(
                "red-bank-develop",
                SIGNING_SECRET,
                NOW.plusSeconds(300),
                List.of("USER"),
                List.of("USER")
        ));

        assertThat(token.subject()).isEqualTo("user-1");
        assertThat(token.profileId()).isEqualTo("profile-1");
        assertThat(token.deviceId()).isEqualTo("device-1");
        assertThat(token.roles()).containsExactly("USER");
    }

    @Test
    void rejectsExpiredAndWrongAudienceTokens() {
        GatewayJwtService service = service("develop", false);

        assertThatThrownBy(() -> service.verifyBearerToken("Bearer " + token(
                "red-bank-develop",
                SIGNING_SECRET,
                NOW.minusSeconds(1),
                List.of("USER"),
                List.of("USER")
        ))).isInstanceOf(GatewayTokenException.class);

        assertThatThrownBy(() -> service.verifyBearerToken("not-bearer"))
                .isInstanceOf(GatewayTokenException.class);
    }

    @Test
    void acceptsDebugIssuerOnlyWhenDevelopDebugIsEnabled() {
        String debugToken = token(
                "red-bank-debug",
                "gateway-debug-signing-secret-at-least-32-bytes",
                NOW.plusSeconds(300),
                List.of("ADMIN"),
                List.of()
        );

        assertThat(service("develop", true).verifyBearerToken("Bearer " + debugToken).roles())
                .containsExactly("ADMIN");
        assertThatThrownBy(() -> service("prod", true).verifyBearerToken("Bearer " + debugToken))
                .isInstanceOf(GatewayTokenException.class);
    }

    private GatewayJwtService service(String profile, boolean debugEnabled) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        JwtProperties jwt = new JwtProperties("red-bank-develop", "red-bank-api", 900, SIGNING_SECRET, null);
        DebugProperties debug = new DebugProperties(
                debugEnabled,
                "gateway-debug-signing-secret-at-least-32-bytes",
                new DebugProperties.MockOtp(false)
        );
        return new GatewayJwtService(
                jwt,
                debug,
                environment,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String token(
            String issuer,
            String secret,
            Instant expiresAt,
            List<String> roles,
            List<String> scopes
    ) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience("red-bank-api")
                .subject("user-1")
                .jwtID("token-1")
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(expiresAt))
                .claim("profile_id", "profile-1")
                .claim("device_id", "device-1")
                .claim("roles", roles)
                .claim("scope", scopes)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
