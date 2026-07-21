package com.redbank.kyc.application;

import java.util.UUID;

public interface OtpChallengeClient {
    PhoneOtpChallenge createPhoneChallenge(UUID onboardingSessionId, String phoneNumber);
}
