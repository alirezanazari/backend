package com.redbank.card.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.card.config.DebugProperties;
import com.redbank.card.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private static final String DEBUG_ISSUER = "red-bank-debug";

    private final JwtProperties jwtProperties;
    private final DebugProperties debugProperties;
    private final Environment environment;
    private final Clock clock;

    public JwtTokenService(
            JwtProperties jwtProperties,
            DebugProperties debugProperties,
            Environment environment,
            Clock clock
    ) {
        this.jwtProperties = jwtProperties;
        this.debugProperties = debugProperties;
        this.environment = environment;
        this.clock = clock;
    }

    public VerifiedToken verifyUserToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasRole("USER")) {
            throw new CardUnauthorizedException("Unauthorized");
        }
        return token;
    }

    public VerifiedToken verifyAdminToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasRole("ADMIN") && !token.hasRole("SYSTEM")) {
            throw new CardUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private VerifiedToken verifyBearerToken(String authorizationHeader) {
        JWTClaimsSet claims = verify(extractBearerToken(authorizationHeader));
        return new VerifiedToken(
                parseUuid(claims.getSubject()),
                parseNullableUuid(stringClaim(claims, "device_id")),
                parseNullableUuid(stringClaim(claims, "profile_id")),
                stringListClaim(claims, "roles"),
                stringListClaim(claims, "scope")
        );
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new CardUnauthorizedException("Unauthorized");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!jwt.verify(new MACVerifier(secretForIssuer(claims.getIssuer())))) {
                throw new CardUnauthorizedException("Unauthorized");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                throw new CardUnauthorizedException("Unauthorized");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.audience())) {
                throw new CardUnauthorizedException("Unauthorized");
            }
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new CardUnauthorizedException("Unauthorized");
        }
    }

    private byte[] secretForIssuer(String issuer) {
        if (jwtProperties.issuer().equals(issuer)) {
            return signingSecret(jwtProperties.signingSecret(), "REDBANK_JWT_SIGNING_SECRET");
        }
        if (DEBUG_ISSUER.equals(issuer) && debugProperties.enabled() && isDevelopProfile()) {
            return signingSecret(debugProperties.jwtSecret(), "REDBANK_DEBUG_JWT_SECRET");
        }
        throw new CardUnauthorizedException("Unauthorized");
    }

    private boolean isDevelopProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("develop");
    }

    private byte[] signingSecret(String value, String name) {
        byte[] secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(name + " must be at least 32 bytes for HS256");
        }
        return secret;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new CardUnauthorizedException("Unauthorized");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new CardUnauthorizedException("Unauthorized");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new CardUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new CardUnauthorizedException("Unauthorized");
        }
    }

    private UUID parseNullableUuid(String value) {
        return value == null || value.isBlank() ? null : parseUuid(value);
    }

    private String stringClaim(JWTClaimsSet claims, String name) {
        Object claim = claims.getClaim(name);
        return claim == null ? null : claim.toString();
    }

    private List<String> stringListClaim(JWTClaimsSet claims, String name) {
        try {
            List<String> values = claims.getStringListClaim(name);
            return values == null ? List.of() : List.copyOf(values);
        } catch (ParseException exception) {
            throw new CardUnauthorizedException("Unauthorized");
        }
    }
}
