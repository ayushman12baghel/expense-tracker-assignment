package com.splitwise.service;

import com.splitwise.dto.AddMemberRequest;
import com.splitwise.dto.CreateGroupRequest;
import com.splitwise.dto.GroupResponse;
import com.splitwise.entity.Group;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.exception.UnauthorizedException;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, User principal) {
        Group group = Group.builder()
                .name(request.getName().trim())
                .createdBy(principal)
                .build();
        
        // Save first to generate UUID
        group = groupRepository.save(group);
        
        // Now add membership with valid group ID
        group.getMemberships().add(new com.splitwise.entity.GroupMembership(group, principal, java.time.LocalDate.now()));
        group = groupRepository.save(group);
        
        return mapToResponse(group);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getUserGroups(User principal) {
        List<Group> groups = groupRepository.findAllByMemberId(principal.getId());
        return groups.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(UUID groupId, User principal) {
        Group group = fetchGroupAndVerifyMembership(groupId, principal);
        return mapToResponse(group);
    }

    @Transactional
    public GroupResponse addMemberByEmail(UUID groupId, AddMemberRequest request, User principal) {
        Group group = fetchGroupAndVerifyMembership(groupId, principal);

        User newMember = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));

        boolean alreadyMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(newMember.getId()) && m.getLeftDate() == null);
        if (!alreadyMember) {
            group.getMemberships().add(new com.splitwise.entity.GroupMembership(group, newMember, java.time.LocalDate.now()));
            group = groupRepository.save(group);
        }
        return mapToResponse(group);
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userIdToRemove, User principal) {
        Group group = fetchGroupAndVerifyMembership(groupId, principal);

        User memberToRemove = userRepository.findById(userIdToRemove)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userIdToRemove));

        com.splitwise.entity.GroupMembership membershipToRemove = group.getMemberships().stream()
                .filter(m -> m.getUser().getId().equals(userIdToRemove) && m.getLeftDate() == null)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("User is not an active member of this group"));

        // TODO: Phase 2 - Check if user's net balance in this group is exactly $0 before allowing removal

        membershipToRemove.setLeftDate(java.time.LocalDate.now());
        groupRepository.save(group);
    }

    /**
     * Helper to fetch a group and verify the principal is a member.
     */
    private Group fetchGroupAndVerifyMembership(UUID groupId, User principal) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(principal.getId()) && m.getLeftDate() == null);

        if (!isMember) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        return group;
    }

    /**
     * Helper to map Group entity to GroupResponse DTO.
     */
    private GroupResponse mapToResponse(Group group) {
        List<GroupResponse.GroupMemberResponse> memberResponses = group.getMemberships().stream()
                .filter(m -> m.getLeftDate() == null)
                .map(m -> GroupResponse.GroupMemberResponse.builder()
                        .id(m.getUser().getId())
                        .name(m.getUser().getName())
                        .email(m.getUser().getEmail())
                        .build())
                .collect(Collectors.toList());

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .createdAt(group.getCreatedAt())
                .members(memberResponses)
                .build();
    }
}
