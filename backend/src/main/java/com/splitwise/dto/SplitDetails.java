package com.splitwise.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SplitDetails {

    @NotNull(message = "User ID is required for split")
    private UUID userId;

    /**
     * The value representing the split.
     * - For EQUAL: Not strictly required, can be ignored or represent shares (usually ignored).
     * - For UNEQUAL: The exact monetary amount.
     * - For PERCENTAGE: The percentage value (e.g., 33.33).
     * - For SHARE: The number of shares.
     */
    private BigDecimal value;
}
