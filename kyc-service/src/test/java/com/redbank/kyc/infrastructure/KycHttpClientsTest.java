package com.redbank.kyc.infrastructure;

import com.redbank.contracts.common.Money;
import com.redbank.kyc.application.ActiveReferrerAccount;
import com.redbank.kyc.application.FeatureFlagSubject;
import com.redbank.kyc.application.KycConflictException;
import com.redbank.kyc.application.KycLimitExceededException;
import com.redbank.kyc.application.PhoneOtpChallenge;
import com.redbank.kyc.application.ReferralRewardCommand;
import com.redbank.kyc.application.ReferralRewardResult;
import com.redbank.kyc.config.FeatureFlagClientProperties;
import com.redbank.kyc.config.IamClientProperties;
import com.redbank.kyc.config.ReferralRewardClientProperties;
import com.redbank.kyc.config.ReferrerAccountClientProperties;
import com.redbank.kyc.infrastructure.account.HttpReferrerAccountClient;
import com.redbank.kyc.infrastructure.featureflag.HttpFeatureFlagClient;
import com.redbank.kyc.infrastructure.iam.HttpOtpChallengeClient;
import com.redbank.kyc.infrastructure.ledger.HttpReferralRewardClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KycHttpClientsTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";
    private static final String REFERRAL_REWARD_ACCOUNT_GET_TOKEN = "test-referral-reward-account-get-token";
    private static final String REFERRAL_REWARD_TOKEN = "test-referral-reward-token";
    private static final String OTP_CHALLENGE_CREATE_TOKEN = "test-otp-challenge-create-token";

    @Test
    void accountClientReturnsCurrencyMatchedActiveAccount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID userId = UUID.randomUUID();
        HttpReferrerAccountClient client = new HttpReferrerAccountClient(
                builder,
                new ReferrerAccountClientProperties(
                        true,
                        "http://account.test",
                        SERVICE_TOKEN,
                        REFERRAL_REWARD_ACCOUNT_GET_TOKEN
                )
        );

        server.expect(once(), requestTo("http://account.test/internal/v1/account/referral/reward-account/get"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", REFERRAL_REWARD_ACCOUNT_GET_TOKEN))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.currency").value("IRR"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "userId": "%s",
                            "accountNumber": "123456789012345678",
                            "currency": "IRR"
                          }
                        }
                        """.formatted(userId), MediaType.APPLICATION_JSON));

        ActiveReferrerAccount account = client.findActiveAccount(userId, "IRR");

        assertThat(account).isEqualTo(new ActiveReferrerAccount(
                userId,
                "123456789012345678",
                "IRR"
        ));
        server.verify();
    }

    @Test
    void accountClientReturnsNullWhenNoEligibleAccountExists() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpReferrerAccountClient client = new HttpReferrerAccountClient(
                builder,
                new ReferrerAccountClientProperties(
                        true,
                        "http://account.test",
                        SERVICE_TOKEN,
                        REFERRAL_REWARD_ACCOUNT_GET_TOKEN
                )
        );

        server.expect(requestTo("http://account.test/internal/v1/account/referral/reward-account/get"))
                .andRespond(withResourceNotFound());

        assertThat(client.findActiveAccount(UUID.randomUUID(), "IRR")).isNull();
        server.verify();
    }

    @Test
    void featureFlagClientEvaluatesExactFlagForTrustedSubject() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID userId = UUID.randomUUID();
        HttpFeatureFlagClient client = new HttpFeatureFlagClient(
                builder,
                new FeatureFlagClientProperties(true, "http://flags.test", SERVICE_TOKEN)
        );

        server.expect(requestTo("http://flags.test/api/v1/feature-flags/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", SERVICE_TOKEN))
                .andExpect(jsonPath("$.context.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.keys[0]").value("referral.reward.enabled"))
                .andExpect(jsonPath("$.data.subjectUserId").value(userId.toString()))
                .andExpect(jsonPath("$.data.profileId").doesNotExist())
                .andExpect(jsonPath("$.data.accountNumber").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "flags": [
                              {
                                "key": "referral.reward.enabled",
                                "enabled": false
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.isEnabled(
                "referral.reward.enabled",
                FeatureFlagSubject.forUser(userId)
        )).isFalse();
        server.verify();
    }

    @Test
    void rewardClientPostsLedgerCommandAndReturnsResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpReferralRewardClient client = new HttpReferralRewardClient(
                builder,
                new ReferralRewardClientProperties(true, "http://ledger.test", REFERRAL_REWARD_TOKEN)
        );
        ReferralRewardCommand command = new ReferralRewardCommand(
                "reward-123",
                "123456789012345678",
                new Money(new BigDecimal("500000"), "IRR")
        );

        server.expect(requestTo("http://ledger.test/internal/v1/ledger/transactions/referral-reward"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", REFERRAL_REWARD_TOKEN))
                .andExpect(jsonPath("$.referralRewardId").value("reward-123"))
                .andExpect(jsonPath("$.referrerExternalAccountNumber").value("123456789012345678"))
                .andExpect(jsonPath("$.amount.amount").value(500000))
                .andExpect(jsonPath("$.amount.currency").value("IRR"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "ledgerTransactionId": "ledger-456",
                            "status": "POSTED"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ReferralRewardResult result = client.requestReward(command);

        assertThat(result).isEqualTo(new ReferralRewardResult("ledger-456", "POSTED"));
        server.verify();
    }

    @Test
    void otpChallengeClientCreatesPhoneChallenge() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID sessionId = UUID.randomUUID();
        HttpOtpChallengeClient client = new HttpOtpChallengeClient(
                builder,
                new IamClientProperties(true, "http://iam.test", OTP_CHALLENGE_CREATE_TOKEN)
        );

        server.expect(once(), requestTo("http://iam.test/internal/v1/iam/otp-challenge/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", OTP_CHALLENGE_CREATE_TOKEN))
                .andExpect(jsonPath("$.onboardingSessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.phoneNumber").value("09121234567"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "challengeId": "challenge-1",
                            "expiresAt": "2026-07-07T10:05:00Z",
                            "resendAvailableAt": "2026-07-07T10:00:45Z"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        PhoneOtpChallenge challenge = client.createPhoneChallenge(sessionId, "09121234567");

        assertThat(challenge).isEqualTo(new PhoneOtpChallenge(
                "challenge-1",
                java.time.Instant.parse("2026-07-07T10:05:00Z"),
                java.time.Instant.parse("2026-07-07T10:00:45Z")
        ));
        server.verify();
    }

    @Test
    void otpChallengeClientMapsConflictAndLimitResponses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpOtpChallengeClient client = new HttpOtpChallengeClient(
                builder,
                new IamClientProperties(true, "http://iam.test", OTP_CHALLENGE_CREATE_TOKEN)
        );

        server.expect(once(), requestTo("http://iam.test/internal/v1/iam/otp-challenge/create"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "context": {
                                    "requestId": "internal",
                                    "correlationId": null,
                                    "profileId": null,
                                    "status": {
                                      "code": "CONFLICT",
                                      "message": "Phone number already exists",
                                      "httpStatus": 409,
                                      "details": []
                                    }
                                  },
                                  "data": null
                                }
                                """));
        server.expect(once(), requestTo("http://iam.test/internal/v1/iam/otp-challenge/create"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "context": {
                                    "requestId": "internal",
                                    "correlationId": null,
                                    "profileId": null,
                                    "status": {
                                      "code": "LIMIT_EXCEEDED",
                                      "message": "OTP resend is not available yet",
                                      "httpStatus": 400,
                                      "details": []
                                    }
                                  },
                                  "data": null
                                }
                                """));

        assertThatThrownBy(() -> client.createPhoneChallenge(UUID.randomUUID(), "09121234567"))
                .isInstanceOf(KycConflictException.class);
        assertThatThrownBy(() -> client.createPhoneChallenge(UUID.randomUUID(), "09121234567"))
                .isInstanceOf(KycLimitExceededException.class);
        server.verify();
    }
}
