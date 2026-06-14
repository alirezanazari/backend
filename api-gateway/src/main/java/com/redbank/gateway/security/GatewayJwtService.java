package com.redbank.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.gateway.config.DebugProperties;
import com.redbank.gateway.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class GatewayJwtService {
    private static final String DEBUG_ISSUER = "red-bank-debug";

    private final JwtProperties jwtProperties;
    private final DebugProperties debugProperties;
    private final Environment environment;
    private final Clock clock;

    @Autowired
    public GatewayJwtService(
            JwtProperties jwtProperties,
            DebugProperties debugProperties,
            Environment environment
    ) {
        this(jwtProperties, debugProperties, environment, Clock.systemUTC());
    }

    GatewayJwtService(
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

    public GatewayToken verifyBearerToken(String authorizationHeader) {
        JWTClaimsSet claims = verify(extractBearerToken(authorizationHeader));
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new GatewayTokenException();
        }
        return new GatewayToken(
                subject,
                stringClaim(claims, "profile_id"),
                stringClaim(claims, "device_id"),
                claims.getJWTID(),
                stringListClaim(claims, "roles"),
                stringListClaim(claims, "scope")
        );
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new GatewayTokenException();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!jwt.verify(new MACVerifier(secretForIssuer(claims.getIssuer())))) {
                throw new GatewayTokenException();
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                throw new GatewayTokenException();
            }
            Date issuedAt = claims.getIssueTime();
            if (issuedAt == null || issuedAt.toInstant().isAfter(clock.instant().plusSeconds(60))) {
                throw new GatewayTokenException();
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.audience())) {
                throw new GatewayTokenException();
            }
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new GatewayTokenException();
        }
    }

    private byte[] secretForIssuer(String issuer) {
        if (jwtProperties.issuer().equals(issuer)) {
            return signingSecret(jwtProperties.signingSecret(), "REDBANK_JWT_SIGNING_SECRET");
        }
        if (DEBUG_ISSUER.equals(issuer) && debugProperties.enabled() && isDevelopProfile()) {
            return signingSecret(debugProperties.jwtSecret(), "REDBANK_DEBUG_JWT_SECRET");
        }
        throw new GatewayTokenException();
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
            throw new GatewayTokenException();
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new GatewayTokenException();
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new GatewayTokenException();
        }
        return token;
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
            throw new GatewayTokenException();
        }
    }
}
