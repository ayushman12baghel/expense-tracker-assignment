package com.splitwise.dto;

import com.splitwise.enums.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Payer ID is required")
    private UUID payerId;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotNull(message = "Split type is required")
    private SplitType splitType;

    @NotEmpty(message = "Splits cannot be empty")
    @Valid
    private List<SplitDetails> splits;
}
