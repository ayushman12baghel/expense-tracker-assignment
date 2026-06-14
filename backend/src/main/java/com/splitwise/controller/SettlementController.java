package com.splitwise.controller;

import com.splitwise.dto.CreateSettlementRequest;
import com.splitwise.dto.SettlementResponse;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final UserRepository userRepository;

    public SettlementController(SettlementService settlementService, UserRepository userRepository) {
        this.settlementService = settlementService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateSettlementRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        SettlementResponse response = settlementService.createSettlement(groupId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SettlementResponse>> getGroupSettlements(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        List<SettlementResponse> response = settlementService.getGroupSettlements(groupId, principal);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{settlementId}/approve")
    public ResponseEntity<SettlementResponse> approveSettlement(
            @PathVariable UUID groupId,
            @PathVariable UUID settlementId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        SettlementResponse response = settlementService.approveSettlement(groupId, settlementId, principal);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{settlementId}/reject")
    public ResponseEntity<SettlementResponse> rejectSettlement(
            @PathVariable UUID groupId,
            @PathVariable UUID settlementId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        SettlementResponse response = settlementService.rejectSettlement(groupId, settlementId, principal);
        return ResponseEntity.ok(response);
    }

    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
