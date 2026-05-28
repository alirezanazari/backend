package com.redbank.account.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder value = new StringBuilder(18);
        value.append(random.nextInt(9) + 1);
        for (int index = 1; index < 18; index++) {
            value.append(random.nextInt(10));
        }
        return value.toString();
    }
}
