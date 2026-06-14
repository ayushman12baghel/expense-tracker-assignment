package com.splitwise.service;

import com.splitwise.dto.SplitDetails;
import com.splitwise.entity.ExpenseSplit;
import com.splitwise.entity.User;
import com.splitwise.enums.SplitType;
import com.splitwise.exception.BadRequestException;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Math Engine: Strictly dedicated to generating precise ExpenseSplit entities.
 * Enforces BigDecimal constraints with RoundingMode.HALF_UP.
 */
@Service
public class SplitCalculationService {

    private final UserRepository userRepository;

    public SplitCalculationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Calculates the splits based on the provided SplitType.
     * Guaranteed to sum exactly to the total amount.
     */
    public List<ExpenseSplit> calculateSplits(BigDecimal totalAmount, UUID payerId,
                                              SplitType splitType, List<SplitDetails> splitDetails) {

        List<ExpenseSplit> splits = switch (splitType) {
            case EQUAL -> calculateEqualSplits(totalAmount, splitDetails);
            case UNEQUAL -> calculateUnequalSplits(totalAmount, splitDetails);
            case PERCENTAGE -> calculatePercentageSplits(totalAmount, splitDetails);
            case SHARE -> calculateShareSplits(totalAmount, splitDetails);
        };

        // Distribute fractional penny remainders to the payer (or the first user if payer not involved)
        distributeRemainder(splits, totalAmount, payerId);

        return splits;
    }

    private List<ExpenseSplit> calculateEqualSplits(BigDecimal totalAmount, List<SplitDetails> splitDetails) {
        BigDecimal splitCount = new BigDecimal(splitDetails.size());
        BigDecimal splitAmount = totalAmount.divide(splitCount, 2, RoundingMode.HALF_UP);

        List<ExpenseSplit> splits = new ArrayList<>();
        for (SplitDetails detail : splitDetails) {
            splits.add(createSplitEntity(detail.getUserId(), splitAmount));
        }
        return splits;
    }

    private List<ExpenseSplit> calculateUnequalSplits(BigDecimal totalAmount, List<SplitDetails> splitDetails) {
        BigDecimal sum = BigDecimal.ZERO;
        List<ExpenseSplit> splits = new ArrayList<>();

        for (SplitDetails detail : splitDetails) {
            if (detail.getValue() == null || detail.getValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Unequal split requires a positive value for each user");
            }
            // Round to 2 decimal places to ensure strict currency formatting
            BigDecimal roundedValue = detail.getValue().setScale(2, RoundingMode.HALF_UP);
            sum = sum.add(roundedValue);
            splits.add(createSplitEntity(detail.getUserId(), roundedValue));
        }

        if (sum.compareTo(totalAmount) != 0) {
            throw new BadRequestException("Sum of unequal splits (" + sum + ") does not match total amount (" + totalAmount + ")");
        }

        return splits;
    }

    private List<ExpenseSplit> calculatePercentageSplits(BigDecimal totalAmount, List<SplitDetails> splitDetails) {
        BigDecimal totalPercentage = BigDecimal.ZERO;
        for (SplitDetails detail : splitDetails) {
            if (detail.getValue() == null || detail.getValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Percentage split requires a positive percentage value for each user");
            }
            totalPercentage = totalPercentage.add(detail.getValue());
        }

        if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
            throw new BadRequestException("Sum of percentages must be exactly 100, got: " + totalPercentage);
        }

        List<ExpenseSplit> splits = new ArrayList<>();
        for (SplitDetails detail : splitDetails) {
            BigDecimal amount = totalAmount.multiply(detail.getValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            splits.add(createSplitEntity(detail.getUserId(), amount));
        }
        return splits;
    }

    private List<ExpenseSplit> calculateShareSplits(BigDecimal totalAmount, List<SplitDetails> splitDetails) {
        BigDecimal totalShares = BigDecimal.ZERO;
        for (SplitDetails detail : splitDetails) {
            if (detail.getValue() == null || detail.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Share split requires a positive share value for each user");
            }
            totalShares = totalShares.add(detail.getValue());
        }

        List<ExpenseSplit> splits = new ArrayList<>();
        for (SplitDetails detail : splitDetails) {
            BigDecimal amount = totalAmount.multiply(detail.getValue())
                    .divide(totalShares, 2, RoundingMode.HALF_UP);
            splits.add(createSplitEntity(detail.getUserId(), amount));
        }
        return splits;
    }

    /**
     * Resolves the "fractional penny" problem (e.g., $100 / 3 = $33.33 each, sum = $99.99).
     * Calculates the remainder and adds it to the payer's split (if they are part of the split),
     * or the first person in the split otherwise.
     */
    private void distributeRemainder(List<ExpenseSplit> splits, BigDecimal totalAmount, UUID payerId) {
        BigDecimal currentSum = splits.stream()
                .map(ExpenseSplit::getAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainder = totalAmount.subtract(currentSum);

        if (remainder.compareTo(BigDecimal.ZERO) != 0) {
            ExpenseSplit targetSplit = splits.stream()
                    .filter(s -> s.getUser().getId().equals(payerId))
                    .findFirst()
                    .orElse(splits.get(0)); // Default to the first person if payer isn't in the split list

            targetSplit.setAmountOwed(targetSplit.getAmountOwed().add(remainder));
        }
    }

    private ExpenseSplit createSplitEntity(UUID userId, BigDecimal amountOwed) {
        // Fetch real user entity to ensure properties like 'name' are available for the frontend response
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.splitwise.exception.BadRequestException("User not found in split calculation"));

        return ExpenseSplit.builder()
                .user(user)
                .amountOwed(amountOwed)
                .build();
    }
}
