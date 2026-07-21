package com.redbank.iam.application;

import com.redbank.contracts.identity.LoginReason;
import com.redbank.iam.api.v1.dto.LoginRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginValidatorTest {
    private final LoginValidator validator = new LoginValidator();

    @Test
    void requiresCredentialsWhenReasonIsAbsent() {
        assertThatThrownBy(() -> validator.validate(new LoginRequest(null, null, null)))
                .isInstanceOf(IamValidationException.class)
                .satisfies(exception -> {
                    IamValidationException validation = (IamValidationException) exception;
                    assertThat(validation.details())
                            .anyMatch(detail -> "data.username".equals(detail.field()))
                            .anyMatch(detail -> "data.password".equals(detail.field()));
                });
    }

    @Test
    void rejectsOutOfBoundsCredentialLengths() {
        assertThatThrownBy(() -> validator.validate(new LoginRequest("ab", "short", null)))
                .isInstanceOf(IamValidationException.class)
                .satisfies(exception -> {
                    IamValidationException validation = (IamValidationException) exception;
                    assertThat(validation.details())
                            .anyMatch(detail -> "data.username".equals(detail.field()))
                            .anyMatch(detail -> "data.password".equals(detail.field()));
                });
    }

    @Test
    void allowsMissingCredentialsWhenReasonIsPresent() {
        assertThatCode(() -> validator.validate(new LoginRequest(null, null, LoginReason.REGISTER)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new LoginRequest("", "", LoginReason.RECOVERY)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNormalCredentialLogin() {
        assertThatCode(() -> validator.validate(new LoginRequest("user_1", "Password1!", null)))
                .doesNotThrowAnyException();
    }
}
