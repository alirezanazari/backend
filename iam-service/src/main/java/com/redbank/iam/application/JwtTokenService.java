package com.redbank.iam.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.iam.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.util.Base64;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    public static final String SCOPE_ACCOUNT_REGISTER = "ONBOARDING_ACCOUNT_REGISTER";
    public static final String SCOPE_DEVICE_VERIFY = "ONBOARDING_DEVICE_VERIFY";

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final RedisService redisService;

    public JwtTokenService(JwtProperties jwtProperties, Clock clock, RedisService redisService) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.redisService = redisService;
    }

    public TokenResult createAccountRequiredToken(UUID userId) {
        return createToken(userId, null, List.of("ONBOARDING"), List.of(SCOPE_ACCOUNT_REGISTER),
                jwtProperties.onboardingTokenTtlSeconds());
    }

    public TokenResult createDeviceVerifyToken(UUID userId, UUID deviceId) {
        return createToken(userId, deviceId, List.of("ONBOARDING"), List.of(SCOPE_DEVICE_VERIFY),
                jwtProperties.onboardingTokenTtlSeconds());
    }

    public TokenResult createUserToken(UUID userId, UUID deviceId) {
        return createUserToken(userId, deviceId, null);
    }

    public TokenResult createUserToken(UUID userId, UUID deviceId, UUID profileId) {
        return createToken(userId, deviceId, profileId, List.of("USER"), List.of("USER"), jwtProperties.accessTokenTtlSeconds());
    }

    private TokenResult createToken(UUID userId, UUID deviceId, List<String> roles, List<String> scopes, long ttlSeconds) {
        return createToken(userId, deviceId, null, roles, scopes, ttlSeconds);
    }

    private TokenResult createToken(
            UUID userId,
            UUID deviceId,
            UUID profileId,
            List<String> roles,
            List<String> scopes,
            long ttlSeconds
    ) {
        byte[] secret = signingSecret();
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.issuer())
                .audience(jwtProperties.audience())
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("device_id", deviceId == null ? null : deviceId.toString())
                .claim("profile_id", profileId == null ? null : profileId.toString())
                .claim("roles", roles)
                .claim("scope", scopes)
                .build();

        try {
            SignedJWT jwt = jwtProperties.usesAsymmetricSigning()
                    ? new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
                    : new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            if (jwtProperties.usesAsymmetricSigning()) {
                jwt.sign(new RSASSASigner(signingPrivateKey()));
            } else {
                jwt.sign(new MACSigner(secret));
            }
            return TokenResult.bearer(jwt.serialize(), expiresAt);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    public VerifiedToken verifyBearerToken(String authorizationHeader, String requiredScope) {
        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims = verify(token);

        String jti = claims.getJWTID();
        if(redisService.isBlacklisted(jti)){
            throw new IamUnauthorizedException("token already is in blacklist");
        }
        Instant expiresAt = claims.getExpirationTime().toInstant();
        UUID userId = parseUuid(claims.getSubject());
        UUID deviceId = parseNullableUuid(stringClaim(claims, "device_id"));
        List<String> scopes = stringListClaim(claims, "scope");
        List<String> roles = stringListClaim(claims, "roles");


        VerifiedToken verifiedToken = new VerifiedToken(jti, expiresAt, userId, deviceId, roles, scopes);
        if (!verifiedToken.hasScope(requiredScope)) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        return verifiedToken;
    }

    private JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!verifySignature(jwt)) {
                throw new IamUnauthorizedException("Unauthorized");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                throw new IamUnauthorizedException("Unauthorized");
            }
            if (!jwtProperties.issuer().equals(claims.getIssuer())) {
                throw new IamUnauthorizedException("Unauthorized");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(jwtProperties.audience())) {
                throw new IamUnauthorizedException("Unauthorized");
            }
            return claims;
        } catch (ParseException | JOSEException exception) {
            throw new IamUnauthorizedException("Unauthorized");
        }
    }

    private boolean verifySignature(SignedJWT jwt) throws JOSEException {
        if (jwtProperties.usesAsymmetricSigning()) {
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IamUnauthorizedException("Unauthorized");
            }
            return jwt.verify(new RSASSAVerifier(verificationPublicKey()));
        }
        if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        return jwt.verify(new MACVerifier(signingSecret()));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        String token = authorizationHeader.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        return token;
    }

    private byte[] signingSecret() {
        byte[] secret = jwtProperties.signingSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("REDBANK_JWT_SIGNING_SECRET must be at least 32 bytes for HS256");
        }
        return secret;
    }

    private RSAPrivateKey signingPrivateKey() {
        try {
            String privateKey = jwtProperties.signingPrivateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(privateKey);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("REDBANK_JWT_PRIVATE_KEY must be a valid RSA private key", exception);
        }
    }

    private RSAPublicKey verificationPublicKey() {
        try {
            String configuredPublicKey = jwtProperties.verificationPublicKey();
            if (configuredPublicKey == null || configuredPublicKey.isBlank()) {
                throw new IllegalStateException("REDBANK_JWT_PUBLIC_KEY is required for RSA JWT verification");
            }
            String publicKey = configuredPublicKey
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

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IamUnauthorizedException("Unauthorized");
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
            throw new IamUnauthorizedException("Unauthorized");
        }
    }
}
