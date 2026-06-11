package com.redbank.card.infrastructure.featureflag;

import com.redbank.card.application.FeatureFlagClient;
import com.redbank.card.application.FeatureFlagSubject;
import com.redbank.card.config.CardProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "redbank.feature-flag-client",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class ConfiguredFeatureFlagClient implements FeatureFlagClient {
    private static final String CARD_ORDER_FLAG = "card.order.enabled";

    private final CardProperties cardProperties;

    public ConfiguredFeatureFlagClient(CardProperties cardProperties) {
        this.cardProperties = cardProperties;
    }

    @Override
    public boolean isEnabled(String key, FeatureFlagSubject subject) {
        return !CARD_ORDER_FLAG.equals(key) || cardProperties.orderEnabled();
    }
}
