package com.redbank.gateway.debug;

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
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("develop")
public class DebugTokenService {
    private static final Logger log = LoggerFactory.getLogger(DebugTokenService.class);

    private final DebugProperties debugProperties;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Autowired
    public DebugTokenService(DebugProperties debugProperties, JwtProperties jwtProperties) {
        this(debugProperties, jwtProperties, Clock.systemUTC());
    }

    DebugTokenService(DebugProperties debugProperties, JwtProperties jwtProperties, Clock clock) {
        this.debugProperties = debugProperties;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public DebugTokenResponse createUserToken(DebugTokenRequest request) {
        return createToken(request, "USER", "debug-device");
    }

    public DebugTokenResponse createAdminToken(DebugTokenRequest request) {
        return createToken(request, "ADMIN", "debug-admin-device");
    }

    public DebugTokenResponse createServiceToken(DebugTokenRequest request) {
        return createToken(request, "INTERNAL_SERVICE", "debug-service-device");
    }

    private DebugTokenResponse createToken(DebugTokenRequest request, String role, String fallbackDeviceId) {
        byte[] secret = debugProperties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("REDBANK_DEBUG_JWT_SECRET must be at least 32 bytes for HS256");
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(request.ttlSeconds() == null ? 900 : request.ttlSeconds());
        List<String> scopes = request.scopes() == null ? List.of() : List.copyOf(request.scopes());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-debug")
                .audience(jwtProperties.audience())
                .subject(request.subject())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("profile_id", request.profileId())
                .claim("device_id", request.deviceId() == null ? fallbackDeviceId : request.deviceId())
                .claim("service_name", request.serviceName())
                .claim("roles", List.of(role))
                .claim("scope", scopes)
                .build();

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            log.info("Generated develop debug token role={} subject={} scopes={} expiresAt={}",
                    role, request.subject(), scopes, expiresAt);
            return new DebugTokenResponse(jwt.serialize(), "Bearer", expiresAt);
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign debug token", exception);
        }
    }
}
