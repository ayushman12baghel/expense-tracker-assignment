package com.splitwise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailResponse {
    private UUID expenseId;
    private LocalDate date;
    private String description;
    private UUID payerId;
    private String payerName;
    private UUID borrowerId;
    private String borrowerName;
    private BigDecimal exactAmountOwed;
}
