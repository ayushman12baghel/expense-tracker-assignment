package com.splitwise.service;

import com.splitwise.dto.CreateExpenseRequest;
import com.splitwise.dto.ExpenseResponse;
import com.splitwise.dto.SplitDetails;
import com.splitwise.entity.Expense;
import com.splitwise.entity.ExpenseSplit;
import com.splitwise.entity.Group;
import com.splitwise.entity.User;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.exception.UnauthorizedException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SplitCalculationService splitCalculationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ExpenseService(ExpenseRepository expenseRepository,
                          GroupRepository groupRepository,
                          UserRepository userRepository,
                          SplitCalculationService splitCalculationService,
                          SimpMessagingTemplate messagingTemplate) {
        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.splitCalculationService = splitCalculationService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ExpenseResponse createExpense(UUID groupId, CreateExpenseRequest request, User principal) {
        Group group = fetchGroupAndVerifyMembership(groupId, principal);

        // Verify the payer is a member of the group
        boolean isPayerMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(request.getPayerId()));
        if (!isPayerMember) {
            throw new BadRequestException("Payer must be a member of the group");
        }

        // Verify all users involved in the split are members of the group
        for (SplitDetails split : request.getSplits()) {
            boolean isSplitUserMember = group.getMemberships().stream()
                    .anyMatch(m -> m.getUser().getId().equals(split.getUserId()));
            if (!isSplitUserMember) {
                throw new BadRequestException("User " + split.getUserId() + " in split is not a member of the group");
            }
        }

        User payer = userRepository.getReferenceById(request.getPayerId());

        Expense expense = Expense.builder()
                .group(group)
                .description(request.getDescription().trim())
                .amount(request.getAmount())
                .payer(payer)
                .date(request.getDate())
                .splitType(request.getSplitType())
                .build();

        List<SplitDetails> finalSplits = request.getSplits();
        
        // Dynamically check the expense's date against joined_date and left_date for EQUAL splits
        if (request.getSplitType() == com.splitwise.enums.SplitType.EQUAL) {
            finalSplits = new java.util.ArrayList<>();
            for (SplitDetails sd : request.getSplits()) {
                boolean activeOnDate = group.getMemberships().stream()
                        .anyMatch(m -> m.getUser().getId().equals(sd.getUserId()) && 
                                       m.getJoinedDate() != null && 
                                       !m.getJoinedDate().isAfter(request.getDate()) && 
                                       (m.getLeftDate() == null || !m.getLeftDate().isBefore(request.getDate())));
                if (activeOnDate) {
                    finalSplits.add(sd);
                }
            }
            if (finalSplits.isEmpty()) {
                throw new BadRequestException("No active members in the group on the expense date");
            }
        }

        // Pass to the Math Engine
        List<ExpenseSplit> splits = splitCalculationService.calculateSplits(
                request.getAmount(), request.getPayerId(), request.getSplitType(), finalSplits);

        // Bidirectional linking (CascadeType.ALL will save the splits automatically)
        for (ExpenseSplit split : splits) {
            split.setExpense(expense);
        }
        expense.setSplits(splits);

        expense = expenseRepository.save(expense);

        // Broadcast real-time event
        messagingTemplate.convertAndSend("/topic/group/" + groupId, "{\"type\": \"EXPENSE_CREATED\"}");

        return mapToResponse(expense);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getGroupExpenses(UUID groupId, User principal) {
        fetchGroupAndVerifyMembership(groupId, principal);
        
        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByDateDescCreatedAtDesc(groupId);
        return expenses.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(UUID expenseId, User principal) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));

        // Verify user is in the group that owns this expense
        fetchGroupAndVerifyMembership(expense.getGroup().getId(), principal);

        return mapToResponse(expense);
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

    private ExpenseResponse mapToResponse(Expense expense) {
        List<ExpenseResponse.ExpenseSplitResponse> splitResponses = expense.getSplits().stream()
                .map(split -> ExpenseResponse.ExpenseSplitResponse.builder()
                        .userId(split.getUser().getId())
                        .userName(split.getUser().getName())
                        .amountOwed(split.getAmountOwed())
                        .build())
                .collect(Collectors.toList());

        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .payerId(expense.getPayer().getId())
                .date(expense.getDate())
                .splitType(expense.getSplitType())
                .createdAt(expense.getCreatedAt())
                .splits(splitResponses)
                .build();
    }
}
