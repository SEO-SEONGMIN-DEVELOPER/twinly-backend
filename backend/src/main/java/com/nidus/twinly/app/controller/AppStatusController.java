package com.nidus.twinly.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "앱")
@RestController
public class AppStatusController {

    @Operation(summary = "앱 진입 가능 여부 확인 (점검·필수 업데이트 필터를 통과하면 200)")
    @GetMapping(value = "/api/v1/app/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of());
    }
}
