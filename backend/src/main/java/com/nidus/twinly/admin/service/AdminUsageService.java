package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.result.AdminUsageResult;
import com.nidus.twinly.admin.reader.AdminUsageReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUsageService {

    private final AdminUsageReader adminUsageReader;

    public AdminUsageResult usage() {
        return adminUsageReader.read();
    }
}
