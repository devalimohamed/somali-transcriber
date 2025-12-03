package com.somtranscriber.auth.controller;

import com.somtranscriber.auth.dto.AcceptInviteRequest;
import com.somtranscriber.auth.dto.CreateInviteRequest;
import com.somtranscriber.auth.dto.CreateInviteResponse;
import com.somtranscriber.auth.dto.TokenResponse;
import com.somtranscriber.auth.service.AuthService;
import com.somtranscriber.common.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/invites")
public class InviteController {

    private final AuthService authService;

    public InviteController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    public CreateInviteResponse createInvite(@RequestBody @Valid CreateInviteRequest request) {
        return authService.createInvite(request, SecurityUtils.currentUser());
    }

    @PostMapping("/accept")
    public TokenResponse acceptInvite(@RequestBody @Valid AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }
}
