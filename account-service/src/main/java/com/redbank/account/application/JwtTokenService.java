package com.redbank.account.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.account.config.DebugProperties;
import com.redbank.account.config.JwtProperties;
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
    public static final String SCOPE_ACCOUNT_REGISTER = "ONBOARDING_ACCOUNT_REGISTER";

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

    public VerifiedToken verifyAccountRegistrationToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasScope(SCOPE_ACCOUNT_REGISTER) && !token.hasRole("USER")) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
        return token;
    }

    public VerifiedToken verifyUserToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasRole("USER")) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private VerifiedToken verifyBearerToken(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims = verify(token);

        UUID userId = parseUuid(claims.getSubject());
        UUID deviceId = parseNullableUuid(stringClaim(claims, "device_id"));
        UUID profileId = parseNullableUuid(stringClaim(claims, "profile_id"));
        List<String> scopes = stringListClaim(claims, "scope");
        List<String> roles = stringListClaim(claims, "roles");

        return new VerifiedToken(userId, deviceId, profileId, roles, scopes);
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new AccountUnauthorizedException("Unauthorized");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!jwt.verify(new MACVerifier(secretForIssuer(claims.getIssuer())))) {
                throw new AccountUnauthorizedException("Unauthorized");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                throw new AccountUnauthorizedException("Unauthorized");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.audience())) {
                throw new AccountUnauthorizedException("Unauthorized");
            }
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
    }

    private byte[] secretForIssuer(String issuer) {
        if (jwtProperties.issuer().equals(issuer)) {
            return signingSecret(jwtProperties.signingSecret(), "REDBANK_JWT_SIGNING_SECRET");
        }
        if (DEBUG_ISSUER.equals(issuer) && debugProperties.enabled() && isDevelopProfile()) {
            return signingSecret(debugProperties.jwtSecret(), "REDBANK_DEBUG_JWT_SECRET");
        }
        throw new AccountUnauthorizedException("Unauthorized");
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
            throw new AccountUnauthorizedException("Unauthorized");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
    }

    private UUID parseNullableUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseUuid(value);
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
            throw new AccountUnauthorizedException("Unauthorized");
        }
    }
}
