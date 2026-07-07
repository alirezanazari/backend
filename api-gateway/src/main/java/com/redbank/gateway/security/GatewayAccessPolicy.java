package com.redbank.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class GatewayAccessPolicy {
    public static final String ACCOUNT_REGISTER_SCOPE = "ONBOARDING_ACCOUNT_REGISTER";
    public static final String DEVICE_VERIFY_SCOPE = "ONBOARDING_DEVICE_VERIFY";
    public static final String REFERRAL_SET_SCOPE = "ONBOARDING_REFERRAL_SET";
    // Scopes carried by the anonymous onboarding token minted by POST /api/v1/iam/login with data.reason.
    public static final String PHONE_SET_SCOPE = "ONBOARDING_PHONE_SET";
    public static final String IAM_REGISTER_SCOPE = "ONBOARDING_IAM_REGISTER";

    public Access access(HttpMethod method, String path) {
        if (!HttpMethod.POST.equals(method)) {
            return Access.NOT_GATEWAY_API;
        }
        if (path.equals("/api/v1/iam/register")
                || path.equals("/api/v1/iam/login")
                || path.equals("/api/v1/kyc/jobs/list")
                || path.equals("/api/v1/security/key-exchange")) {
            return Access.PUBLIC;
        }
        if (path.equals("/api/v1/iam/device-verify")) {
            return Access.ONBOARDING_DEVICE;
        }
        if (path.equals("/api/v1/account/register")) {
            return Access.ONBOARDING_ACCOUNT;
        }
        if (path.equals("/api/v1/kyc/referral/set")) {
            return Access.ONBOARDING_REFERRAL;
        }
        if (path.equals("/api/v1/card/unblock")
                || path.equals("/api/v1/card/expire")
                || path.equals("/api/v1/kyc/complete")
                || path.equals("/api/v1/feature-flags/list")
                || path.equals("/api/v1/feature-flags/update")
                || path.equals("/api/v1/feature-flags/targeting/get")
                || path.equals("/api/v1/feature-flags/targeting/update")) {
            return Access.ADMIN;
        }
        if (isPublicServicePath(path)) {
            return Access.USER;
        }
        return Access.NOT_GATEWAY_API;
    }

    public String rateLimitRouteKey(HttpMethod method, String path, Access access) {
        if (access == Access.NOT_GATEWAY_API) {
            return method + ":not-gateway-api";
        }
        if (!HttpMethod.POST.equals(method)) {
            return method + ":not-post";
        }
        if (isExactGatewayPath(path)) {
            return method + ":" + path;
        }
        return method + ":" + gatewayServiceFamily(path);
    }

    public boolean permits(Access access, GatewayToken token) {
        return switch (access) {
            case PUBLIC, NOT_GATEWAY_API -> true;
            case USER -> token.hasRole("USER");
            case ADMIN -> token.hasRole("ADMIN") || token.hasRole("SYSTEM");
            case ONBOARDING_ACCOUNT ->
                    token.hasRole("USER") || token.hasScope(ACCOUNT_REGISTER_SCOPE);
            case ONBOARDING_DEVICE -> token.hasScope(DEVICE_VERIFY_SCOPE);
            case ONBOARDING_REFERRAL -> token.hasScope(REFERRAL_SET_SCOPE);
        };
    }

    private boolean isExactGatewayPath(String path) {
        return path.equals("/api/v1/iam/register")
                || path.equals("/api/v1/iam/login")
                || path.equals("/api/v1/kyc/jobs/list")
                || path.equals("/api/v1/security/key-exchange")
                || path.equals("/api/v1/iam/device-verify")
                || path.equals("/api/v1/account/register")
                || path.equals("/api/v1/kyc/referral/set")
                || path.equals("/api/v1/card/unblock")
                || path.equals("/api/v1/card/expire")
                || path.equals("/api/v1/kyc/complete")
                || path.equals("/api/v1/feature-flags/list")
                || path.equals("/api/v1/feature-flags/update")
                || path.equals("/api/v1/feature-flags/targeting/get")
                || path.equals("/api/v1/feature-flags/targeting/update");
    }

    private String gatewayServiceFamily(String path) {
        if (!path.startsWith("/api/v1/")) {
            return "/api/v1/_unknown";
        }
        String remainder = path.substring("/api/v1/".length());
        int slash = remainder.indexOf('/');
        String service = slash >= 0 ? remainder.substring(0, slash) : remainder;
        if (service.isBlank()) {
            return "/api/v1/_unknown";
        }
        return "/api/v1/" + service + "/_wildcard";
    }

    private boolean isPublicServicePath(String path) {
        return path.startsWith("/api/v1/iam/")
                || path.startsWith("/api/v1/account/")
                || path.startsWith("/api/v1/profile/")
                || path.startsWith("/api/v1/card/")
                || path.startsWith("/api/v1/kyc/")
                || path.startsWith("/api/v1/feature-flags/")
                || path.startsWith("/api/v1/pfm/")
                || path.startsWith("/api/v1/transfers/");
    }

    public enum Access {
        NOT_GATEWAY_API,
        PUBLIC,
        ONBOARDING_ACCOUNT,
        ONBOARDING_DEVICE,
        ONBOARDING_REFERRAL,
        USER,
        ADMIN
    }
}
