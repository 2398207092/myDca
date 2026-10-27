package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /** 账户列表（含各账户资产与人民币合计） */
    @GetMapping
    public ApiResponse<List<AccountDTO>> list(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success(accountService.listAccounts(userId));
    }

    @PostMapping
    public ApiResponse<AccountDTO> create(@Valid @RequestBody CreateAccountReq req,
                                          HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("创建成功", accountService.createAccount(req, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountDTO> update(@PathVariable String id,
                                          @RequestBody UpdateAccountReq req,
                                          HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return ApiResponse.success("更新成功", accountService.updateAccount(id, req, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        accountService.deleteAccount(id, userId);
        return ApiResponse.success(null);
    }
}
