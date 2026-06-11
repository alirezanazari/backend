package com.redbank.pfm.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.envelope.StatusCode;
import org.springframework.web.client.RestClientResponseException;

public record HttpApiError(StatusCode code, String message) {
    public static HttpApiError from(ObjectMapper objectMapper, RestClientResponseException exception) {
        try {
            JsonNode status = objectMapper.readTree(exception.getResponseBodyAsString())
                    .path("context")
                    .path("status");
            String codeValue = status.path("code").asText(null);
            StatusCode code = codeValue == null ? null : StatusCode.valueOf(codeValue);
            String message = status.path("message").asText(exception.getStatusText());
            return new HttpApiError(code, message);
        } catch (Exception exceptionToIgnore) {
            return new HttpApiError(null, exception.getStatusText());
        }
    }
}
