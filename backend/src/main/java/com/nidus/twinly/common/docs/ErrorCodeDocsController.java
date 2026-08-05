package com.nidus.twinly.common.docs;

import com.nidus.twinly.common.web.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Hidden
@RestController
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true")
public class ErrorCodeDocsController {

    @GetMapping("/docs/openapi-error-specifications")
    public ErrorCodeDocsResponse errorCodes() {
        List<ErrorCodeDoc> errors = Arrays.stream(ErrorCode.values())
                .map(ErrorCodeDoc::of)
                .toList();

        return new ErrorCodeDocsResponse(errors);
    }
}
