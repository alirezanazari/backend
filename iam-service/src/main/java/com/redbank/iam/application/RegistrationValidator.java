package com.redbank.iam.application;

import com.redbank.contracts.common.Location;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RegistrationValidator {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,79}$");
    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern NATIONAL_ID = Pattern.compile("^[0-9]{10}$");

    public void validate(RegisterRequest request) {
        List<StatusDetail> details = new ArrayList<>();
        if (!matches(USERNAME, request.username())) {
            details.add(detail("data.username", "username must be 3-80 letters, digits, dot, underscore, or dash", "USERNAME_PATTERN"));
        }
        if (!matches(PHONE_NUMBER, request.phoneNumber())) {
            details.add(detail("data.phoneNumber", "phone number must be 10-15 digits and may start with +", "PHONE_PATTERN"));
        }
        if (!matches(NATIONAL_ID, request.nationalId())) {
            details.add(detail("data.nationalId", "national ID must be exactly 10 digits", "NATIONAL_ID_PATTERN"));
        }
        validatePassword(request.password(), details);
        validateLocation(request.location(), details);

        if (!details.isEmpty()) {
            throw new IamValidationException(details);
        }
    }

    private void validatePassword(String password, List<StatusDetail> details) {
        if (password == null) {
            return;
        }
        boolean hasSpecial = password.chars().anyMatch(ch->
            !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)
        );
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        if (!hasSpecial || !hasDigit || !hasLower || !hasUpper) {
            details.add(detail("data.password", "password must contain at least one special character and one digit & character", "PASSWORD_COMPLEXITY"));
        }
    }

    private void validateLocation(Location location, List<StatusDetail> details) {
        if (location == null) {
            return;
        }
        if (isBlank(location.province())) {
            details.add(detail("data.location.province", "province is required", "LOCATION_PROVINCE_REQUIRED"));
        }
        if (isBlank(location.city())) {
            details.add(detail("data.location.city", "city is required", "LOCATION_CITY_REQUIRED"));
        }
        if (isBlank(location.address())) {
            details.add(detail("data.location.address", "address is required", "LOCATION_ADDRESS_REQUIRED"));
        }
    }

    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private StatusDetail detail(String field, String message, String code) {
        return new StatusDetail(field, message, code);
    }
}
