package com.somtranscriber.auth.controller;

import com.somtranscriber.auth.dto.LoginRequest;
import com.somtranscriber.auth.dto.LogoutRequest;
import com.somtranscriber.auth.dto.RefreshRequest;
import com.somtranscriber.auth.dto.TokenResponse;
import com.somtranscriber.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request);
    }

}
