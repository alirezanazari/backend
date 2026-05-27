package com.redbank.iam.application;

import com.redbank.contracts.envelope.DeviceContext;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeviceContextValidator {
    public String requireFingerprint(RequestContext context) {
        DeviceContext device = context.device();
        if (device == null || device.fingerprint() == null || device.fingerprint().isBlank()) {
            throw new IamValidationException(List.of(new StatusDetail(
                    "context.device.fingerprint",
                    "device fingerprint is required",
                    "DEVICE_FINGERPRINT_REQUIRED"
            )));
        }
        return device.fingerprint();
    }
}
