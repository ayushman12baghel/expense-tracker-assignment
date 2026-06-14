package com.splitwise.controller;

import com.splitwise.dto.ImportReportResponse;
import com.splitwise.entity.User;
import com.splitwise.service.CsvImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class CsvImportController {

    private final CsvImportService csvImportService;
    private final com.splitwise.repository.UserRepository userRepository;

    public CsvImportController(CsvImportService csvImportService, com.splitwise.repository.UserRepository userRepository) {
        this.csvImportService = csvImportService;
        this.userRepository = userRepository;
    }

    @PostMapping("/import")
    public ResponseEntity<ImportReportResponse> importExpenses(
            @RequestParam("groupId") UUID groupId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "confirm", defaultValue = "false") boolean confirm,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails) {
        try {
            User principal = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
            ImportReportResponse report = csvImportService.importCsv(groupId, file, principal, confirm);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
