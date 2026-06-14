package com.splitwise.controller;

import com.splitwise.dto.CreateExpenseRequest;
import com.splitwise.dto.ExpenseResponse;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    public ExpenseController(ExpenseService expenseService, UserRepository userRepository) {
        this.expenseService = expenseService;
        this.userRepository = userRepository;
    }

    @PostMapping("/groups/{groupId}/expenses")
    public ResponseEntity<ExpenseResponse> createExpense(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateExpenseRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        ExpenseResponse response = expenseService.createExpense(groupId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/groups/{groupId}/expenses")
    public ResponseEntity<List<ExpenseResponse>> getGroupExpenses(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        List<ExpenseResponse> response = expenseService.getGroupExpenses(groupId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expenses/{id}")
    public ResponseEntity<ExpenseResponse> getExpenseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        ExpenseResponse response = expenseService.getExpenseById(id, principal);
        return ResponseEntity.ok(response);
    }

    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
