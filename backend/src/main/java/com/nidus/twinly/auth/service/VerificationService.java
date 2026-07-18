package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.dto.command.VerifyCommand;
import com.nidus.twinly.auth.entity.VerificationSession;
import com.nidus.twinly.common.domain.VerificationType;

public interface VerificationService {

    public VerificationSession verify(VerifyCommand command, VerificationType type);
}
