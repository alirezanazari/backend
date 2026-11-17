package com.redbank.account.application;

import java.math.BigDecimal;

public record TopUpPolicy (boolean enabled,
                           BigDecimal minAmount,
                           BigDecimal maxAmount){
}
