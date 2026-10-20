package com.nidus.twinly.auth.service;

import com.nidus.twinly.auth.domain.VerifiedIdentity;
import com.nidus.twinly.auth.dto.result.IdentityAuthUrlResult;

public interface IdentityVerificationProvider {

    IdentityAuthUrlResult requestAuthUrl(String requestNo);

    VerifiedIdentity fetchResult(String requestNo, String transactionId, String webTransactionId);
}
