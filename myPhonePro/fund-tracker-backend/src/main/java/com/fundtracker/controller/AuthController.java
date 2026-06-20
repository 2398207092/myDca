package com.fundtracker.controller;

import com.fundtracker.model.dto.ApiResponse;
import com.fundtracker.model.dto.TokenResp;
import com.fundtracker.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/token")
    public ApiResponse<TokenResp> getToken() {
        return ApiResponse.success(authService.getToken());
    }
}
