package com.splitwise.controller;

import com.splitwise.dto.AddMemberRequest;
import com.splitwise.dto.CreateGroupRequest;
import com.splitwise.dto.GroupResponse;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    public GroupController(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        GroupResponse response = groupService.createGroup(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> getUserGroups(
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        List<GroupResponse> response = groupService.getUserGroups(principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroupById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        GroupResponse response = groupService.getGroupById(id, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<GroupResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        GroupResponse response = groupService.addMemberByEmail(id, request, principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User principal = getUserFromDetails(userDetails);
        groupService.removeMember(id, userId, principal);
        return ResponseEntity.ok().build();
    }

    /**
     * Helper to load the current User entity from the DB using the principal's username (email).
     */
    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }
}
