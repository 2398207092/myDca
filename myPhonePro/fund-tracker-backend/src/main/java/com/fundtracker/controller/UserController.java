package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileDTO> getProfile() {
        return ApiResponse.success(userService.getProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileDTO> updateProfile(@RequestBody UpdateUserProfileReq req) {
        return ApiResponse.success(userService.updateProfile(req));
    }

    @GetMapping("/settings")
    public ApiResponse<UserSettingsDTO> getSettings() {
        return ApiResponse.success(userService.getSettings());
    }

    @PutMapping("/settings")
    public ApiResponse<UserSettingsDTO> updateSettings(@RequestBody UpdateUserSettingsReq req) {
        return ApiResponse.success(userService.updateSettings(req));
    }
}
