package com.splitwise.dto;

import com.splitwise.enums.SplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponse {

    private UUID id;
    private String description;
    private BigDecimal amount;
    private UUID payerId;
    private LocalDate date;
    private SplitType splitType;
    private LocalDateTime createdAt;
    private List<ExpenseSplitResponse> splits;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpenseSplitResponse {
        private UUID userId;
        private String userName;
        private BigDecimal amountOwed;
    }
}
