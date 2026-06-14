package com.splitwise.service;

import com.splitwise.dto.DebtResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceServiceTest {

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        // We only test the algorithmic logic, so we don't need the mocked repositories
        balanceService = new BalanceService(null, null, null);
    }

    @Test
    void test3PersonCycleResolvesToZero() {
        // A owes B $10. B owes C $10. C owes A $10.
        // Net balances should all be exactly 0, and simplified debts should be empty.
        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();

        netBalances.put(userA, BigDecimal.ZERO); // A: -10 (owes B) + 10 (owed by C) = 0
        netBalances.put(userB, BigDecimal.ZERO); // B: +10 (owed by A) - 10 (owes C) = 0
        netBalances.put(userC, BigDecimal.ZERO); // C: +10 (owed by B) - 10 (owes A) = 0

        List<DebtResponse> simplified = balanceService.calculateSimplifiedDebts(netBalances);

        assertTrue(simplified.isEmpty(), "Cycle should resolve to 0 debts");
    }

    @Test
    void testStandardCascade() {
        // A pays $100 for A, B, C, D equally ($25 each).
        // Net balances: A (+75), B (-25), C (-25), D (-25).
        // Simplified: B owes A 25, C owes A 25, D owes A 25.
        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();
        UUID userD = UUID.randomUUID();

        netBalances.put(userA, new BigDecimal("75.00"));
        netBalances.put(userB, new BigDecimal("-25.00"));
        netBalances.put(userC, new BigDecimal("-25.00"));
        netBalances.put(userD, new BigDecimal("-25.00"));

        List<DebtResponse> simplified = balanceService.calculateSimplifiedDebts(netBalances);

        assertEquals(3, simplified.size());

        // Validate that B, C, and D all owe exactly 25.00 to A
        for (DebtResponse debt : simplified) {
            assertEquals(userA, debt.getTo(), "Everyone should owe User A");
            assertTrue(debt.getFrom().equals(userB) || debt.getFrom().equals(userC) || debt.getFrom().equals(userD));
            assertEquals(new BigDecimal("25.00"), debt.getAmount());
        }
    }

    @Test
    void testComplexSettlement() {
        // A is owed +50
        // B is owed +20
        // C owes -40
        // D owes -30
        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID userC = UUID.randomUUID();
        UUID userD = UUID.randomUUID();

        netBalances.put(userA, new BigDecimal("50.00"));
        netBalances.put(userB, new BigDecimal("20.00"));
        netBalances.put(userC, new BigDecimal("-40.00"));
        netBalances.put(userD, new BigDecimal("-30.00"));

        List<DebtResponse> simplified = balanceService.calculateSimplifiedDebts(netBalances);

        // Expected matches (greedy algorithm taking largest magnitudes first):
        // 1. C (-40) and A (+50) -> C owes A 40. A remaining: +10. C remaining: 0.
        // 2. D (-30) and B (+20) -> D owes B 20. B remaining: 0. D remaining: -10.
        // 3. D (-10) and A (+10) -> D owes A 10. Both remaining: 0.
        
        assertEquals(3, simplified.size());
        
        // Sum of all debts settled should equal the total absolute debt (70.00)
        BigDecimal totalSettled = simplified.stream()
                .map(DebtResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        assertEquals(new BigDecimal("70.00"), totalSettled);
    }
}
