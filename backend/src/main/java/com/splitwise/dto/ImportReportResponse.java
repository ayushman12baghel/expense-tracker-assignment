package com.splitwise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportReportResponse {
    private int totalProcessed;
    private List<String> successfulImports;
    private List<String> anomaliesDetected;
}
