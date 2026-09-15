package com.nidus.twinly.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
public class NiceIdentityReturnController {

    private static final String RETURN_PAGE = page("본인인증이 완료되었습니다.", "앱으로 돌아가 주세요.");
    private static final String CLOSE_PAGE = page("본인인증을 종료했습니다.", "앱으로 돌아가 주세요.");

    @Operation(summary = "본인인증 return URL (NICE 표준창 리다이렉트용, 앱이 직접 호출하지 않음)",
            description = "NICE 표준창이 인증 완료 후 브라우저를 보내는 주소. 앱은 웹뷰에서 이 URL로의 이동을 가로채 web_transaction_id 를 꺼내므로 정상 경로에서는 로드되지 않는다. 가로채기에 실패했을 때 사용자에게 보여줄 안내 HTML 만 돌려준다.")
    @GetMapping(value = "/api/v1/auth/onboarding/identity/return", produces = MediaType.TEXT_HTML_VALUE)
    public String identityReturn() {
        return RETURN_PAGE;
    }

    @Operation(summary = "본인인증 close URL (NICE 표준창 닫기 버튼용, 앱이 직접 호출하지 않음)",
            description = "NICE 표준창의 닫기 버튼이 브라우저를 보내는 주소. 앱은 웹뷰에서 이 URL로의 이동을 가로채 웹뷰를 닫으므로 정상 경로에서는 로드되지 않는다.")
    @GetMapping(value = "/api/v1/auth/onboarding/identity/close", produces = MediaType.TEXT_HTML_VALUE)
    public String identityClose() {
        return CLOSE_PAGE;
    }

    private static String page(String title, String message) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                </head>
                <body style="margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;font-family:sans-serif;text-align:center;">
                <div>
                <h1 style="font-size:20px;">%s</h1>
                <p style="font-size:16px;color:#555;">%s</p>
                </div>
                </body>
                </html>
                """.formatted(title, title, message);
    }
}
