package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.dto.result.AdminDashboardResult;
import com.nidus.twinly.admin.reader.AdminDashboardReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminDashboardReader adminDashboardReader;

    public AdminDashboardResult dashboard() {
        return adminDashboardReader.read();
    }
}
