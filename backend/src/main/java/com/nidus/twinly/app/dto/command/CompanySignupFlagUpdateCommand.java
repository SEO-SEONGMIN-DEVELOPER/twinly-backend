package com.nidus.twinly.app.dto.command;

import com.nidus.twinly.app.dto.request.CompanySignupFlagUpdateRequest;

public record CompanySignupFlagUpdateCommand(
        boolean enabled
) {
    public static CompanySignupFlagUpdateCommand from(CompanySignupFlagUpdateRequest request) {
        return new CompanySignupFlagUpdateCommand(request.enabled());
    }
}
