package com.splitwise.service;

import com.splitwise.dto.BalanceResponse;
import com.splitwise.dto.DebtResponse;
import com.splitwise.entity.Expense;
import com.splitwise.entity.ExpenseSplit;
import com.splitwise.entity.Group;
import com.splitwise.entity.Settlement;
import com.splitwise.entity.SettlementStatus;
import com.splitwise.entity.User;
import com.splitwise.exception.ResourceNotFoundException;
import com.splitwise.exception.UnauthorizedException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

@Service
public class BalanceService {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public BalanceService(GroupRepository groupRepository,
                          ExpenseRepository expenseRepository,
                          SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * Calculates the user's net balance and the simplified debt graph for the entire group.
     */
    @Transactional(readOnly = true)
    public BalanceResponse getGroupBalances(UUID groupId, User principal) {
        Group group = fetchGroupAndVerifyMembership(groupId, principal);

        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByDateDescCreatedAtDesc(groupId);
        List<Settlement> settlements = settlementRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId);

        // Step 1: Compute Net Balances
        Map<UUID, BigDecimal> netBalances = computeNetBalances(expenses, settlements);

        // Calculate personal net balance
        BigDecimal personalNetBalance = netBalances.getOrDefault(principal.getId(), BigDecimal.ZERO);

        // Step 2 & 3: The Greedy Minimization Algorithm
        List<DebtResponse> simplifiedDebts = calculateSimplifiedDebts(netBalances);

        return BalanceResponse.builder()
                .netBalance(personalNetBalance.setScale(2, RoundingMode.HALF_UP))
                .simplifiedDebts(simplifiedDebts)
                .build();
    }

    /**
     * Step 1: Compute absolute Net Balances.
     */
    public Map<UUID, BigDecimal> computeNetBalances(List<Expense> expenses, List<Settlement> settlements) {
        Map<UUID, BigDecimal> balances = new HashMap<>();

        // Process Expenses
        for (Expense expense : expenses) {
            UUID payerId = expense.getPayer().getId();
            
            // Payer gets credited (+) the total amount initially
            balances.merge(payerId, expense.getAmount(), BigDecimal::add);

            // Each person owes (-) their split amount
            for (ExpenseSplit split : expense.getSplits()) {
                UUID splitUserId = split.getUser().getId();
                balances.merge(splitUserId, split.getAmountOwed().negate(), BigDecimal::add);
            }
        }

        // Process Settlements
        for (Settlement settlement : settlements) {
            if (settlement.getStatus() != SettlementStatus.APPROVED) {
                continue;
            }

            UUID payerId = settlement.getPayer().getId();
            UUID payeeId = settlement.getPayee().getId();

            // Payer paid debt, so their balance goes up (+)
            balances.merge(payerId, settlement.getAmount(), BigDecimal::add);
            
            // Payee received money, so their balance goes down (-)
            balances.merge(payeeId, settlement.getAmount().negate(), BigDecimal::add);
        }

        // Clean up balances very close to 0 to prevent floating/decimal dust
        balances.entrySet().removeIf(entry -> 
                entry.getValue().abs().compareTo(new BigDecimal("0.01")) < 0);

        return balances;
    }

    /**
     * Record to hold the user and their balance magnitude for PriorityQueues.
     */
    private record BalanceEntry(UUID userId, BigDecimal amount) implements Comparable<BalanceEntry> {
        @Override
        public int compareTo(BalanceEntry other) {
            // Sort descending by magnitude (largest amounts first)
            return other.amount.compareTo(this.amount);
        }
    }

    /**
     * Steps 2 & 3: The Greedy Minimization Algorithm using Max-Heaps.
     */
    public List<DebtResponse> calculateSimplifiedDebts(Map<UUID, BigDecimal> netBalances) {
        PriorityQueue<BalanceEntry> creditors = new PriorityQueue<>(); // Owed money (+)
        PriorityQueue<BalanceEntry> debtors = new PriorityQueue<>();   // Owes money (-)

        // Split into Creditors and Debtors
        for (Map.Entry<UUID, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal balance = entry.getValue();
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new BalanceEntry(entry.getKey(), balance));
            } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                // Store absolute value for debtors for easy max-heap extraction
                debtors.add(new BalanceEntry(entry.getKey(), balance.abs()));
            }
        }

        List<DebtResponse> simplifiedDebts = new ArrayList<>();

        // Greedy matching
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            BalanceEntry maxCreditor = creditors.poll();
            BalanceEntry maxDebtor = debtors.poll();

            // The settled amount is the minimum of what the debtor owes and what the creditor is owed
            BigDecimal settledAmount = maxCreditor.amount.min(maxDebtor.amount);

            simplifiedDebts.add(DebtResponse.builder()
                    .from(maxDebtor.userId())
                    .to(maxCreditor.userId())
                    .amount(settledAmount.setScale(2, RoundingMode.HALF_UP))
                    .build());

            // Subtract settled amount
            BigDecimal newCreditorBalance = maxCreditor.amount.subtract(settledAmount);
            BigDecimal newDebtorBalance = maxDebtor.amount.subtract(settledAmount);

            // If there's still balance left, push back into the heap
            if (newCreditorBalance.compareTo(new BigDecimal("0.01")) >= 0) {
                creditors.add(new BalanceEntry(maxCreditor.userId(), newCreditorBalance));
            }
            if (newDebtorBalance.compareTo(new BigDecimal("0.01")) >= 0) {
                debtors.add(new BalanceEntry(maxDebtor.userId(), newDebtorBalance));
            }
        }

        return simplifiedDebts;
    }

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
     * Audit Trail: Returns the exact bilateral expenses and settlements between two specific users in a group.
     */
    @Transactional(readOnly = true)
    public List<com.splitwise.dto.AuditTrailResponse> getAuditTrail(UUID groupId, UUID user1Id, UUID user2Id, User principal) {
        fetchGroupAndVerifyMembership(groupId, principal);

        List<Expense> expenses = expenseRepository.findBilateralExpenses(groupId, user1Id, user2Id);
        List<Settlement> settlements = settlementRepository.findBilateralSettlements(groupId, user1Id, user2Id);

        List<com.splitwise.dto.AuditTrailResponse> auditTrail = new ArrayList<>();

        for (Expense expense : expenses) {
            UUID payerId = expense.getPayer().getId();
            UUID borrowerId = payerId.equals(user1Id) ? user2Id : user1Id;

            // Find how much the borrower owes in this specific expense
            BigDecimal amountOwed = expense.getSplits().stream()
                    .filter(s -> s.getUser() != null && s.getUser().getId().equals(borrowerId))
                    .map(ExpenseSplit::getAmountOwed)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (amountOwed.compareTo(BigDecimal.ZERO) > 0) {
                User borrower = expense.getSplits().stream()
                        .filter(s -> s.getUser() != null && s.getUser().getId().equals(borrowerId))
                        .map(ExpenseSplit::getUser)
                        .findFirst().orElse(null);

                auditTrail.add(com.splitwise.dto.AuditTrailResponse.builder()
                        .expenseId(expense.getId())
                        .date(expense.getDate())
                        .description(expense.getDescription())
                        .payerId(payerId)
                        .payerName(expense.getPayer().getName())
                        .borrowerId(borrowerId)
                        .borrowerName(borrower != null ? borrower.getName() : "Unknown")
                        .exactAmountOwed(amountOwed.setScale(2, RoundingMode.HALF_UP))
                        .build());
            }
        }

        for (Settlement settlement : settlements) {
            if (settlement.getStatus() != SettlementStatus.APPROVED) {
                continue;
            }
            auditTrail.add(com.splitwise.dto.AuditTrailResponse.builder()
                    .expenseId(settlement.getId())
                    .date(settlement.getCreatedAt().toLocalDate())
                    .description("Settlement Payment")
                    .payerId(settlement.getPayer().getId())
                    .payerName(settlement.getPayer().getName())
                    .borrowerId(settlement.getPayee().getId()) // payee receives the money, so they "borrow" in our schema terms for audit direction
                    .borrowerName(settlement.getPayee().getName())
                    .exactAmountOwed(settlement.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        // Sort combined list by date descending
        auditTrail.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return auditTrail;
    }
}
