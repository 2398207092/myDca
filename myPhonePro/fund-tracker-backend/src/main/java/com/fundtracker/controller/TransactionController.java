package com.fundtracker.controller;

import com.fundtracker.model.dto.*;
import com.fundtracker.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ApiResponse<List<TransactionDTO>> listTransactions(
            @RequestParam(required = false) String holdingId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ApiResponse.success(
                transactionService.listTransactions(holdingId, type, dateFrom, dateTo));
    }

    @PostMapping
    public ApiResponse<TransactionDTO> createTransaction(
            @Valid @RequestBody CreateTransactionReq req) {
        return ApiResponse.success("添加成功", transactionService.createTransaction(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<TransactionDTO> updateTransaction(
            @PathVariable String id,
            @RequestBody UpdateTransactionReq req) {
        return ApiResponse.success("更新成功", transactionService.updateTransaction(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteResp> deleteTransaction(@PathVariable String id) {
        transactionService.deleteTransaction(id);
        return ApiResponse.success(new DeleteResp(true));
    }
}
