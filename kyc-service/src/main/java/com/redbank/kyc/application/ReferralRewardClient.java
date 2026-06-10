package com.redbank.kyc.application;

public interface ReferralRewardClient {
    ReferralRewardResult requestReward(ReferralRewardCommand command);
}
