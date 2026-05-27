package com.redbank.iam.application;

import com.redbank.contracts.common.Location;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationValidatorTest {
    private final RegistrationValidator validator = new RegistrationValidator();

    @Test
    void rejectsPasswordWithoutLetterAndDigit() {
        RegisterRequest request = new RegisterRequest(
                "1234567890",
                "09121234567",
                "user_1",
                "passwordonly",
                "Ali",
                "Nazari",
                new Location("IR", "Tehran", "Tehran", "Main street", "1234567890"),
                "job-1"
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IamValidationException.class)
                .satisfies(exception -> {
                    IamValidationException validation = (IamValidationException) exception;
                    org.assertj.core.api.Assertions.assertThat(validation.details())
                            .anyMatch(detail -> "data.password".equals(detail.field()));
                });
    }

    @Test
    void rejectsInvalidIdentityFields() {
        RegisterRequest request = new RegisterRequest(
                "abc",
                "phone",
                ".bad",
                "Password1",
                "Ali",
                "Nazari",
                new Location("IR", "Tehran", "Tehran", "Main street", "1234567890"),
                "job-1"
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IamValidationException.class)
                .satisfies(exception -> {
                    IamValidationException validation = (IamValidationException) exception;
                    org.assertj.core.api.Assertions.assertThat(validation.details())
                            .extracting("field")
                            .contains("data.username", "data.phoneNumber", "data.nationalId");
                });
    }
}
