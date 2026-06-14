package com.splitwise.controller;

import com.splitwise.dto.BalanceResponse;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
public class BalanceController {

    private final BalanceService balanceService;
    private final UserRepository userRepository;

    public BalanceController(BalanceService balanceService, UserRepository userRepository) {
        this.balanceService = balanceService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<BalanceResponse> getGroupBalances(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        BalanceResponse response = balanceService.getGroupBalances(groupId, principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit")
    public ResponseEntity<java.util.List<com.splitwise.dto.AuditTrailResponse>> getAuditTrail(
            @PathVariable UUID groupId,
            @org.springframework.web.bind.annotation.RequestParam UUID user1Id,
            @org.springframework.web.bind.annotation.RequestParam UUID user2Id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        java.util.List<com.splitwise.dto.AuditTrailResponse> response = balanceService.getAuditTrail(groupId, user1Id, user2Id, principal);
        return ResponseEntity.ok(response);
    }

    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
