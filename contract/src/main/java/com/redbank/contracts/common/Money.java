package com.redbank.contracts.common;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record Money(
        @NotNull @DecimalMin(value = "0.00") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
