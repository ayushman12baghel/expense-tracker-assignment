package com.splitwise.service;

import com.splitwise.dto.SplitDetails;
import com.splitwise.entity.ExpenseSplit;
import com.splitwise.entity.User;
import com.splitwise.enums.SplitType;
import com.splitwise.exception.BadRequestException;
import com.splitwise.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SplitCalculationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SplitCalculationService splitCalculationService;

    private UUID user1;
    private UUID user2;
    private UUID user3;
    private UUID payer;

    @BeforeEach
    void setUp() {
        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
        user3 = UUID.randomUUID();
        payer = user1;

        // Mock the proxy generation (lenient because some tests fail early and don't hit this)
        org.mockito.Mockito.lenient().when(userRepository.getReferenceById(any(UUID.class))).thenAnswer(invocation -> {
            User u = new User();
            u.setId(invocation.getArgument(0));
            return u;
        });
    }

    @Test
    void testEqualSplitWithRemainder() {
        // $100 / 3 = $33.33 with $0.01 remainder.
        // Payer (user1) should get the extra penny: $33.34.
        BigDecimal total = new BigDecimal("100.00");
        List<SplitDetails> details = Arrays.asList(
                new SplitDetails(user1, null),
                new SplitDetails(user2, null),
                new SplitDetails(user3, null)
        );

        List<ExpenseSplit> splits = splitCalculationService.calculateSplits(total, payer, SplitType.EQUAL, details);

        assertEquals(3, splits.size());

        ExpenseSplit split1 = getSplitForUser(splits, user1);
        ExpenseSplit split2 = getSplitForUser(splits, user2);
        ExpenseSplit split3 = getSplitForUser(splits, user3);

        assertEquals(new BigDecimal("33.34"), split1.getAmountOwed());
        assertEquals(new BigDecimal("33.33"), split2.getAmountOwed());
        assertEquals(new BigDecimal("33.33"), split3.getAmountOwed());
    }

    @Test
    void testUnequalSplitSuccess() {
        BigDecimal total = new BigDecimal("100.00");
        List<SplitDetails> details = Arrays.asList(
                new SplitDetails(user1, new BigDecimal("20.00")),
                new SplitDetails(user2, new BigDecimal("30.00")),
                new SplitDetails(user3, new BigDecimal("50.00"))
        );

        List<ExpenseSplit> splits = splitCalculationService.calculateSplits(total, payer, SplitType.UNEQUAL, details);

        assertEquals(new BigDecimal("20.00"), getSplitForUser(splits, user1).getAmountOwed());
        assertEquals(new BigDecimal("30.00"), getSplitForUser(splits, user2).getAmountOwed());
        assertEquals(new BigDecimal("50.00"), getSplitForUser(splits, user3).getAmountOwed());
    }

    @Test
    void testUnequalSplitFailure() {
        BigDecimal total = new BigDecimal("100.00");
        List<SplitDetails> details = Arrays.asList(
                new SplitDetails(user1, new BigDecimal("20.00")),
                new SplitDetails(user2, new BigDecimal("30.00")),
                new SplitDetails(user3, new BigDecimal("49.00")) // Sum is 99.00
        );

        assertThrows(BadRequestException.class, () ->
                splitCalculationService.calculateSplits(total, payer, SplitType.UNEQUAL, details));
    }

    @Test
    void testPercentageSplitSuccess() {
        // 100 * 33.33% = 33.33 each, but sum is 99.99%.
        // The service demands EXACTLY 100%. Let's pass 33.33, 33.33, 33.34
        BigDecimal total = new BigDecimal("200.00");
        List<SplitDetails> details = Arrays.asList(
                new SplitDetails(user1, new BigDecimal("33.33")),
                new SplitDetails(user2, new BigDecimal("33.33")),
                new SplitDetails(user3, new BigDecimal("33.34"))
        );

        List<ExpenseSplit> splits = splitCalculationService.calculateSplits(total, payer, SplitType.PERCENTAGE, details);

        assertEquals(new BigDecimal("66.66"), getSplitForUser(splits, user1).getAmountOwed());
        assertEquals(new BigDecimal("66.66"), getSplitForUser(splits, user2).getAmountOwed());
        // $200 * 33.34% = 66.68.
        assertEquals(new BigDecimal("66.68"), getSplitForUser(splits, user3).getAmountOwed());
    }

    @Test
    void testPercentageSplitFailure() {
        BigDecimal total = new BigDecimal("100.00");
        List<SplitDetails> details = Arrays.asList(
                new SplitDetails(user1, new BigDecimal("33.33")),
                new SplitDetails(user2, new BigDecimal("33.33")),
                new SplitDetails(user3, new BigDecimal("33.33")) // Sum is 99.99, fails validation
        );

        assertThrows(BadRequestException.class, () ->
                splitCalculationService.calculateSplits(total, payer, SplitType.PERCENTAGE, details));
    }

    private ExpenseSplit getSplitForUser(List<ExpenseSplit> splits, UUID userId) {
        return splits.stream()
                .filter(s -> s.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
