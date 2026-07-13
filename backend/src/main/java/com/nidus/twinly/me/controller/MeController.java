package com.nidus.twinly.me.controller;

import com.nidus.twinly.me.dto.command.MeProfileCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoCommitCommand;
import com.nidus.twinly.me.dto.command.MeProfilePhotoPresignCommand;
import com.nidus.twinly.me.dto.request.MeProfileRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoCommitRequest;
import com.nidus.twinly.me.dto.request.MeProfilePhotoPresignRequest;
import com.nidus.twinly.me.dto.response.MeProfileEditResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoCommitResponse;
import com.nidus.twinly.me.dto.response.MeProfilePhotoPresignResponse;
import com.nidus.twinly.me.dto.response.MeWithdrawResponse;
import com.nidus.twinly.me.service.MeService;
import com.nidus.twinly.user.annotation.CurrentUser;
import com.nidus.twinly.user.dto.header.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;

    @PostMapping("/api/v1/me/profile/photo/presign")
    public MeProfilePhotoPresignResponse profilePhotoPresign(@CurrentUser UserInfo userInfo,
                                                              @RequestBody MeProfilePhotoPresignRequest request) {
        return MeProfilePhotoPresignResponse.from(meService.profilePhotoPresign(userInfo.id(), MeProfilePhotoPresignCommand.from(request)));
    }

    @PostMapping("/api/v1/me/profile/photo/commit")
    public MeProfilePhotoCommitResponse profilePhotoCommit(@CurrentUser UserInfo userInfo,
                                                            @RequestBody MeProfilePhotoCommitRequest request) {
        return MeProfilePhotoCommitResponse.from(meService.profilePhotoCommit(userInfo.id(), MeProfilePhotoCommitCommand.from(request)));
    }

    @DeleteMapping("/api/v1/me")
    public MeWithdrawResponse withdraw(@CurrentUser UserInfo userInfo) {
        return MeWithdrawResponse.from(meService.withdraw(userInfo.id()));
    }

    @GetMapping("/api/v1/me/profile-edit")
    public MeProfileEditResponse profileEdit(@CurrentUser UserInfo userInfo) {
        return MeProfileEditResponse.from(meService.profileEdit(userInfo.id()));
    }

    @PatchMapping("/api/v1/me/profile")
    public void profile(@CurrentUser UserInfo userInfo,
                        @RequestBody MeProfileRequest request) {
        meService.profile(userInfo.id(), MeProfileCommand.from(request));
    }

    @PostMapping("/api/v1/me/restore")
    public void restore(@CurrentUser UserInfo userInfo) {
        meService.restore(userInfo.id());
    }
}
