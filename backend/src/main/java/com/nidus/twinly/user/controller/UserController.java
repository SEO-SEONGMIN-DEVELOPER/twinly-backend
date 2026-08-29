package com.nidus.twinly.user.controller;

import com.nidus.twinly.common.web.RequestId;
import com.nidus.twinly.user.dto.response.UsersResponse;
import com.nidus.twinly.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/internal/v1/users")
    public UsersResponse users(@RequestParam(required = false) String cursor,
                               @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        return UsersResponse.from(userService.users(RequestId.toLongOrNull(cursor, "cursor"), limit));
    }
}
