package com.redbank.account.application;

import com.redbank.account.config.AccountProperties;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class IbanGenerator {
    private final AccountProperties accountProperties;

    public IbanGenerator(AccountProperties accountProperties) {
        this.accountProperties = accountProperties;
    }

    public String generate(String accountNumber) {
        String countryCode = accountProperties.ibanCountryCode();
        String bban = accountProperties.bankCode() + accountNumber;
        String checkInput = bban + countryCodeToNumber(countryCode) + "00";
        int checkDigits = 98 - new BigInteger(checkInput).mod(BigInteger.valueOf(97)).intValue();
        return countryCode + "%02d".formatted(checkDigits) + bban;
    }

    public boolean isValid(String iban) {
        if (iban == null || iban.length() < 4) {
            return false;
        }
        String normalized = iban.replace(" ", "").toUpperCase();
        String rearranged = normalized.substring(4) + countryCodeToNumber(normalized.substring(0, 2)) + normalized.substring(2, 4);
        return new BigInteger(rearranged).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private String countryCodeToNumber(String countryCode) {
        StringBuilder result = new StringBuilder();
        for (char character : countryCode.toCharArray()) {
            if (character < 'A' || character > 'Z') {
                throw new IllegalStateException("IBAN country code must contain letters only");
            }
            result.append(character - 'A' + 10);
        }
        return result.toString();
    }
}
