package com.nidus.twinly.auth.dto.command;

import java.util.UUID;

public interface VerifyCommand {
    UUID verificationToken();
    String value();
}