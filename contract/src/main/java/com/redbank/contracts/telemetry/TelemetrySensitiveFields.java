package com.redbank.contracts.telemetry;

import java.util.List;
import java.util.Locale;

public final class TelemetrySensitiveFields {
    public static final List<String> BLOCKED_FIELD_TOKENS = List.of(
            "password",
            "otp",
            "authorization",
            "access_token",
            "accesstoken",
            "refresh_token",
            "refreshtoken",
            "private_token",
            "privatetoken",
            "service_token",
            "servicetoken",
            "token",
            "secret",
            "key_material",
            "keymaterial",
            "phone_number",
            "phonenumber",
            "national_id",
            "nationalid",
            "account_number",
            "accountnumber",
            "card_number",
            "cardnumber",
            "pan",
            "cvv",
            "kyc_evidence",
            "kycevidence",
            "database_password",
            "databasepassword"
    );

    private TelemetrySensitiveFields() {
    }

    public static boolean isBlocked(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String normalized = normalize(fieldName);
        return BLOCKED_FIELD_TOKENS.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String fieldName) {
        return fieldName
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(".", "_")
                .replace("[", "_")
                .replace("]", "_");
    }
}
