package com.redbank.featureflag.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.featureflag.config.DebugProperties;
import com.redbank.featureflag.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
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

    public VerifiedToken verifyAdminToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasRole("ADMIN") && !token.hasRole("SYSTEM")) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
        return token;
    }

    public VerifiedToken verifyUserToken(String authorizationHeader) {
        VerifiedToken token = verifyBearerToken(authorizationHeader);
        if (!token.hasRole("USER")) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private VerifiedToken verifyBearerToken(String authorizationHeader) {
        JWTClaimsSet claims = verify(extractBearerToken(authorizationHeader));
        return new VerifiedToken(
                parseUuid(claims.getSubject()),
                parseNullableUuid(stringClaim(claims, "profile_id")),
                stringListClaim(claims, "roles")
        );
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!verifySignature(jwt, claims.getIssuer())) {
                throw new FeatureFlagUnauthorizedException("Unauthorized");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                throw new FeatureFlagUnauthorizedException("Unauthorized");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.audience())) {
                throw new FeatureFlagUnauthorizedException("Unauthorized");
            }
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
    }

    private boolean verifySignature(SignedJWT jwt, String issuer) throws JOSEException {
        if (jwtProperties.issuer().equals(issuer)) {
            if (jwtProperties.usesAsymmetricVerification()) {
                if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                    throw new FeatureFlagUnauthorizedException("Unauthorized");
                }
                return jwt.verify(new RSASSAVerifier(verificationPublicKey()));
            }
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new FeatureFlagUnauthorizedException("Unauthorized");
            }
            return jwt.verify(new MACVerifier(signingSecret(jwtProperties.signingSecret(), "REDBANK_JWT_SIGNING_SECRET")));
        }
        if (DEBUG_ISSUER.equals(issuer) && debugProperties.enabled() && isDevelopProfile()) {
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new FeatureFlagUnauthorizedException("Unauthorized");
            }
            return jwt.verify(new MACVerifier(signingSecret(debugProperties.jwtSecret(), "REDBANK_DEBUG_JWT_SECRET")));
        }
        throw new FeatureFlagUnauthorizedException("Unauthorized");
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

    private RSAPublicKey verificationPublicKey() {
        try {
            String publicKey = jwtProperties.verificationPublicKey()
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(publicKey);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("REDBANK_JWT_PUBLIC_KEY must be a valid RSA public key", exception);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new FeatureFlagUnauthorizedException("Unauthorized");
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
            throw new FeatureFlagUnauthorizedException("Unauthorized");
        }
    }
}
