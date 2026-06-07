package com.redbank.card.application;

import com.redbank.card.config.CardProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class CardNumberGenerator {
    private final SecureRandom secureRandom = new SecureRandom();
    private final CardProperties cardProperties;

    public CardNumberGenerator(CardProperties cardProperties) {
        this.cardProperties = cardProperties;
    }

    public GeneratedCardNumber generate() {
        StringBuilder pan = new StringBuilder(cardProperties.bin());
        while (pan.length() < 16) {
            pan.append(secureRandom.nextInt(10));
        }
        String maskedPan = pan.substring(0, 6) + "******" + pan.substring(12);
        return new GeneratedCardNumber(maskedPan);
    }

    public record GeneratedCardNumber(String maskedPan) {
    }
}
