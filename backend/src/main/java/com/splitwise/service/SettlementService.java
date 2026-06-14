package com.splitwise.service;

import com.splitwise.dto.CreateSettlementRequest;
import com.splitwise.dto.SettlementResponse;
import com.splitwise.entity.Group;
import com.splitwise.entity.Settlement;
import com.splitwise.entity.SettlementStatus;
import com.splitwise.entity.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.exception.UnauthorizedException;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SettlementService(SettlementRepository settlementRepository,
                             GroupRepository groupRepository,
                             UserRepository userRepository,
                             SimpMessagingTemplate messagingTemplate) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public SettlementResponse createSettlement(UUID groupId, CreateSettlementRequest request, User principal) {
        if (request.getPayerId().equals(request.getPayeeId())) {
            throw new BadRequestException("Payer and Payee cannot be the same");
        }

        if (!principal.getId().equals(request.getPayerId()) && !principal.getId().equals(request.getPayeeId())) {
            throw new UnauthorizedException("You must be either the payer or the payee to record a settlement");
        }

        Group group = fetchGroupAndVerifyMembership(groupId, principal);

        boolean isPayerMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(request.getPayerId()));
        boolean isPayeeMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(request.getPayeeId()));

        if (!isPayerMember || !isPayeeMember) {
            throw new BadRequestException("Both payer and payee must be members of this group");
        }

        User payer = userRepository.getReferenceById(request.getPayerId());
        User payee = userRepository.getReferenceById(request.getPayeeId());

        SettlementStatus status = principal.getId().equals(request.getPayeeId()) 
                ? SettlementStatus.APPROVED 
                : SettlementStatus.PENDING;

        Settlement settlement = Settlement.builder()
                .group(group)
                .payer(payer)
                .payee(payee)
                .amount(request.getAmount())
                .status(status)
                .build();

        settlement = settlementRepository.save(settlement);

        messagingTemplate.convertAndSend("/topic/group/" + groupId, "{\"type\": \"SETTLEMENT_CREATED\"}");

        return mapToResponse(settlement);
    }

    @Transactional
    public SettlementResponse approveSettlement(UUID groupId, UUID settlementId, User principal) {
        fetchGroupAndVerifyMembership(groupId, principal);
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
        
        if (!settlement.getGroup().getId().equals(groupId)) {
            throw new BadRequestException("Settlement does not belong to this group");
        }
        
        if (!settlement.getPayee().getId().equals(principal.getId())) {
            throw new UnauthorizedException("Only the payee can approve a settlement");
        }
        
        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new BadRequestException("Settlement is not pending approval");
        }
        
        settlement.setStatus(SettlementStatus.APPROVED);
        settlement = settlementRepository.save(settlement);
        
        messagingTemplate.convertAndSend("/topic/group/" + groupId, "{\"type\": \"SETTLEMENT_APPROVED\"}");
        
        return mapToResponse(settlement);
    }

    @Transactional
    public SettlementResponse rejectSettlement(UUID groupId, UUID settlementId, User principal) {
        fetchGroupAndVerifyMembership(groupId, principal);
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
        
        if (!settlement.getGroup().getId().equals(groupId)) {
            throw new BadRequestException("Settlement does not belong to this group");
        }
        
        if (!settlement.getPayee().getId().equals(principal.getId())) {
            throw new UnauthorizedException("Only the payee can reject a settlement");
        }
        
        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new BadRequestException("Settlement is not pending approval");
        }
        
        settlement.setStatus(SettlementStatus.REJECTED);
        settlement = settlementRepository.save(settlement);
        
        messagingTemplate.convertAndSend("/topic/group/" + groupId, "{\"type\": \"SETTLEMENT_REJECTED\"}");
        
        return mapToResponse(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getGroupSettlements(UUID groupId, User principal) {
        fetchGroupAndVerifyMembership(groupId, principal);

        List<Settlement> settlements = settlementRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId);
        return settlements.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private Group fetchGroupAndVerifyMembership(UUID groupId, User principal) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        boolean isMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(principal.getId()));

        if (!isMember) {
            throw new UnauthorizedException("You are not a member of this group");
        }

        return group;
    }

    private SettlementResponse mapToResponse(Settlement settlement) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .groupId(settlement.getGroup().getId())
                .payerId(settlement.getPayer().getId())
                .payeeId(settlement.getPayee().getId())
                .amount(settlement.getAmount())
                .status(settlement.getStatus())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
