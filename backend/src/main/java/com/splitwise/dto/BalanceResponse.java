package com.splitwise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {

    /**
     * The authenticated user's net balance in the group.
     * Positive = They are owed money.
     * Negative = They owe money.
     */
    private BigDecimal netBalance;

    /**
     * The list of simplified debts for the entire group.
     */
    private List<DebtResponse> simplifiedDebts;
}
