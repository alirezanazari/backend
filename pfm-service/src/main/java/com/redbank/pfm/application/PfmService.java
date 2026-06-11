package com.redbank.pfm.application;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Image;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.pfm.BoxPresetSummary;
import com.redbank.contracts.pfm.BoxStatus;
import com.redbank.contracts.pfm.BoxSummary;
import com.redbank.pfm.api.v1.dto.CreateBoxRequest;
import com.redbank.pfm.api.v1.dto.CreateBoxResponse;
import com.redbank.pfm.api.v1.dto.EditBoxRequest;
import com.redbank.pfm.api.v1.dto.EditBoxResponse;
import com.redbank.pfm.api.v1.dto.ListBoxPresetsRequest;
import com.redbank.pfm.api.v1.dto.ListBoxPresetsResponse;
import com.redbank.pfm.api.v1.dto.ListBoxesRequest;
import com.redbank.pfm.api.v1.dto.ListBoxesResponse;
import com.redbank.pfm.api.v1.dto.ListPfmTransactionsRequest;
import com.redbank.pfm.api.v1.dto.ListPfmTransactionsResponse;
import com.redbank.pfm.config.PfmProperties;
import com.redbank.pfm.domain.BoxPresetRecord;
import com.redbank.pfm.domain.BoxRecord;
import com.redbank.pfm.infrastructure.BoxPresetRepository;
import com.redbank.pfm.infrastructure.BoxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PfmService {
    private final BoxPresetRepository boxPresetRepository;
    private final BoxRepository boxRepository;
    private final JwtTokenService jwtTokenService;
    private final VaultAccountClient vaultAccountClient;
    private final LedgerPfmClient ledgerPfmClient;
    private final PfmProperties pfmProperties;
    private final Clock clock;

    public PfmService(
            BoxPresetRepository boxPresetRepository,
            BoxRepository boxRepository,
            JwtTokenService jwtTokenService,
            VaultAccountClient vaultAccountClient,
            LedgerPfmClient ledgerPfmClient,
            PfmProperties pfmProperties,
            Clock clock
    ) {
        this.boxPresetRepository = boxPresetRepository;
        this.boxRepository = boxRepository;
        this.jwtTokenService = jwtTokenService;
        this.vaultAccountClient = vaultAccountClient;
        this.ledgerPfmClient = ledgerPfmClient;
        this.pfmProperties = pfmProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListBoxPresetsResponse listPresets(
            String authorizationHeader,
            RequestContext context,
            ListBoxPresetsRequest request
    ) {
        // Auth
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        resolveProfileId(token, context);

        // Locale
        String language = requestedLanguage(context, request);
        List<BoxPresetRecord> presets = boxPresetRepository.findActive(language);
        if (presets.isEmpty() && !pfmProperties.defaultLanguage().equals(language)) {
            presets = boxPresetRepository.findActive(pfmProperties.defaultLanguage());
        }
        return new ListBoxPresetsResponse(presets.stream().map(this::toPresetSummary).toList());
    }

    @Transactional
    public CreateBoxResponse createBox(
            String authorizationHeader,
            RequestContext context,
            CreateBoxRequest request
    ) {
        // Auth
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);

        // Input
        String title = normalizeTitle(request.title(), "data.title");
        String currency = normalizeCurrency(request.currency());
        BoxPresetRecord preset = resolvePreset(request.presetId());
        Image image = request.image() == null && preset != null ? preset.image() : request.image();

        // Account
        AccountSummary account = vaultAccountClient.create(token.userId(), profileId, currency);
        validateVaultAccount(account, profileId, currency);

        // Ledger
        ledgerPfmClient.createAccount(account.accountNumber(), token.userId(), currency);

        // Persistence
        Instant now = clock.instant();
        BoxRecord box = new BoxRecord(
                UUID.randomUUID(),
                token.userId(),
                profileId,
                account.accountNumber(),
                title,
                preset == null ? null : preset.id(),
                image,
                BoxStatus.ACTIVE,
                now,
                now
        );
        try {
            boxRepository.save(box);
        } catch (DataIntegrityViolationException exception) {
            throw new PfmConflictException("Box account relation already exists");
        }
        return new CreateBoxResponse(toBoxSummary(box, null), account);
    }

    @Transactional(readOnly = true)
    public ListBoxesResponse listBoxes(
            String authorizationHeader,
            RequestContext context,
            ListBoxesRequest request
    ) {
        // Auth
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);

        // Query
        ListBoxesRequest filters = request == null
                ? new ListBoxesRequest(List.of(), false)
                : request;
        List<BoxRecord> boxes = boxRepository.findByOwner(
                token.userId(),
                profileId,
                filters.statuses()
        );

        // Balance
        boolean includeBalance = Boolean.TRUE.equals(filters.includeBalance());
        return new ListBoxesResponse(boxes.stream()
                .map(box -> toBoxSummary(
                        box,
                        includeBalance ? ledgerPfmClient.getBalance(box.accountNumber()) : null
                ))
                .toList());
    }

    @Transactional
    public EditBoxResponse editBox(
            String authorizationHeader,
            RequestContext context,
            EditBoxRequest request
    ) {
        // Auth
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = resolveProfileId(token, context);

        // Ownership
        UUID boxId = parseUuid(request.boxId(), "data.boxId", "BOX_ID_FORMAT");
        BoxRecord box = boxRepository.findByIdAndOwner(boxId, token.userId(), profileId)
                .orElseThrow(() -> new PfmNotFoundException("Box was not found"));
        if (box.status() != BoxStatus.ACTIVE) {
            throw new PfmBusinessRuleException("Inactive box cannot be edited");
        }

        // Input
        if (request.title() == null && request.presetId() == null && request.image() == null) {
            throw validation("data", "at least one editable field is required", "BOX_EDIT_REQUIRED");
        }
        String title = request.title() == null
                ? box.title()
                : normalizeTitle(request.title(), "data.title");
        BoxPresetRecord preset = request.presetId() == null ? null : resolvePreset(request.presetId());
        UUID presetId = preset == null ? box.presetId() : preset.id();
        Image image = request.image() != null
                ? request.image()
                : preset == null ? box.image() : preset.image();
        BoxRecord edited = box.withDisplayMetadata(title, presetId, image, clock.instant());

        // Persistence
        if (!boxRepository.updateDisplayMetadata(edited)) {
            throw new PfmConflictException("Box changed while it was being edited");
        }
        return new EditBoxResponse(toBoxSummary(edited, null));
    }

    public ListPfmTransactionsResponse listTransactions(
            String authorizationHeader,
            RequestContext context,
            ListPfmTransactionsRequest request
    ) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    private UUID resolveProfileId(VerifiedToken token, RequestContext context) {
        String contextProfileId = context.account() == null ? null : context.account().profileId();
        UUID profileId = contextProfileId == null || contextProfileId.isBlank()
                ? token.profileId()
                : parseUuid(contextProfileId, "context.account.profileId", "PROFILE_ID_FORMAT");
        if (profileId == null) {
            throw new PfmBusinessRuleException("Active profile is required");
        }
        if (token.profileId() != null && !token.profileId().equals(profileId)) {
            throw new PfmUnauthorizedException("Unauthorized");
        }
        return profileId;
    }

    private String requestedLanguage(RequestContext context, ListBoxPresetsRequest request) {
        String requestLanguage = request == null ? null : trimToNull(request.language());
        if (requestLanguage != null) {
            return requestLanguage;
        }
        String contextLanguage = context.app() == null ? null : trimToNull(context.app().language());
        return contextLanguage == null ? pfmProperties.defaultLanguage() : contextLanguage;
    }

    private BoxPresetRecord resolvePreset(String presetId) {
        String value = trimToNull(presetId);
        if (value == null) {
            return null;
        }
        UUID id = parseUuid(value, "data.presetId", "PRESET_ID_FORMAT");
        return boxPresetRepository.findActiveById(id)
                .orElseThrow(() -> new PfmNotFoundException("Box preset was not found"));
    }

    private void validateVaultAccount(AccountSummary account, UUID profileId, String currency) {
        if (account == null
                || !profileId.toString().equals(account.profileId())
                || account.type() != AccountType.VAULT
                || account.status() != AccountStatus.ACTIVE
                || !currency.equals(account.currency())
                || account.accountNumber() == null
                || !account.accountNumber().matches("\\d{18}")) {
            throw new PfmExternalServiceException("Account service returned an invalid vault");
        }
    }

    private BoxPresetSummary toPresetSummary(BoxPresetRecord preset) {
        return new BoxPresetSummary(
                preset.id().toString(),
                preset.title(),
                preset.image(),
                preset.sortOrder()
        );
    }

    private BoxSummary toBoxSummary(BoxRecord box, BalanceSummary balance) {
        return new BoxSummary(
                box.id().toString(),
                box.accountNumber(),
                box.title(),
                box.image(),
                box.status(),
                balance
        );
    }

    private String normalizeTitle(String value, String field) {
        String title = trimToNull(value);
        if (title == null) {
            throw validation(field, "must not be blank", "BOX_TITLE_REQUIRED");
        }
        return title;
    }

    private String normalizeCurrency(String value) {
        String currency = trimToNull(value);
        currency = currency == null ? pfmProperties.defaultCurrency() : currency;
        if (currency.length() != 3) {
            throw validation("data.currency", "must contain 3 characters", "CURRENCY_LENGTH");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private UUID parseUuid(String value, String field, String code) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation(field, "must be a UUID", code);
        }
    }

    private PfmValidationException validation(String field, String message, String code) {
        return new PfmValidationException(List.of(new StatusDetail(field, message, code)));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
