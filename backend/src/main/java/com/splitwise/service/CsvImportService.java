package com.splitwise.service;

import com.opencsv.CSVReader;
import com.splitwise.dto.ImportReportResponse;
import com.splitwise.entity.Group;
import com.splitwise.entity.User;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.splitwise.dto.CreateExpenseRequest;
import com.splitwise.dto.SplitDetails;
import com.splitwise.enums.SplitType;
import com.splitwise.service.ExpenseService;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CsvImportService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExpenseService expenseService;

    public CsvImportService(GroupRepository groupRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, ExpenseService expenseService) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expenseService = expenseService;
    }

    @Transactional
    public ImportReportResponse importCsv(UUID groupId, MultipartFile file, User principal) throws Exception {
        List<String> successfulImports = new ArrayList<>();
        List<String> anomaliesDetected = new ArrayList<>();
        int totalProcessed = 0;

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));

        // Deduplication tracker: Date|PayerEmail|Amount
        Set<String> processedRecords = new HashSet<>();

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] headers = csvReader.readNext();

            int dateIdx = -1, descIdx = -1, amountIdx = -1, currIdx = -1, payerIdx = -1;
            int splitTypeIdx = -1, splitWithIdx = -1, splitDetailsIdx = -1;
            if (headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].toLowerCase().trim();
                    if (h.equals("date") || h.startsWith("date"))
                        dateIdx = i;
                    else if (h.equals("description") || h.equals("desc") || (h.contains("desc") && !h.contains("split")))
                        descIdx = i;
                    else if (h.contains("amount") || h.contains("cost"))
                        amountIdx = i;
                    else if (h.contains("curr"))
                        currIdx = i;
                    else if (h.contains("paid") || h.contains("payer") || h.contains("by"))
                        payerIdx = i;
                    else if (h.equals("split_type") || h.equals("split type"))
                        splitTypeIdx = i;
                    else if (h.equals("split_with") || h.equals("split with"))
                        splitWithIdx = i;
                    else if (h.equals("split_details") || h.equals("split details"))
                        splitDetailsIdx = i;
                }
            }

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                totalProcessed++;
                try {
                    String rawDate = (dateIdx >= 0 && line.length > dateIdx) ? line[dateIdx].trim() : "";
                    String description = (descIdx >= 0 && line.length > descIdx) ? line[descIdx].trim() : "";
                    String rawAmount = (amountIdx >= 0 && line.length > amountIdx) ? line[amountIdx].trim() : "0";
                    String currency = (currIdx >= 0 && line.length > currIdx) ? line[currIdx].trim() : "";
                    String paidBy = (payerIdx >= 0 && line.length > payerIdx) ? line[payerIdx].trim() : "";
                    String splitType = (splitTypeIdx >= 0 && line.length > splitTypeIdx) ? line[splitTypeIdx].trim() : "";
                    String splitWith = (splitWithIdx >= 0 && line.length > splitWithIdx) ? line[splitWithIdx].trim() : "";
                    String splitDetailsRaw = (splitDetailsIdx >= 0 && line.length > splitDetailsIdx) ? line[splitDetailsIdx].trim() : "";

                    // Anomaly 3: Missing Payer
                    if (paidBy.isEmpty()) {
                        anomaliesDetected.add(description + " - Missing Payer: paid_by is blank. Rejected.");
                        continue;
                    }

                    // Anomaly 2: Number Formatting
                    rawAmount = rawAmount.replace(",", "");
                    BigDecimal amount = new BigDecimal(rawAmount);

                    // Anomaly 7: Negative Value
                    if (amount.compareTo(BigDecimal.ZERO) < 0) {
                        anomaliesDetected
                                .add(description + " - Negative amount rejected: amounts must be strictly positive.");
                        continue;
                    }

                    // Anomaly 6: Missing Currency
                    if (currency.isEmpty()) {
                        currency = "INR";
                        anomaliesDetected.add(description + " - Missing Currency: Defaulted to INR.");
                    }

                    // Anomaly 5: Foreign Currency
                    if ("USD".equalsIgnoreCase(currency)) {
                        amount = amount.multiply(new BigDecimal("95")).setScale(2, RoundingMode.HALF_UP);
                        anomaliesDetected.add(
                                description + " - Foreign Currency USD converted to INR using strict multiplier 95.");
                    }

                    // Anomaly 8: Bad Date Format
                    LocalDate parsedDate;
                    try {
                        parsedDate = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (DateTimeParseException e) {
                        try {
                            String[] parts = rawDate.split("-");
                            if (parts.length == 2) {
                                String mmm = parts[0];
                                String dd = parts[1];
                                parsedDate = LocalDate.parse(dd + "-" + mmm + "-2026",
                                        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
                                anomaliesDetected.add(description + " - Date format corrected from " + rawDate + " to "
                                        + parsedDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                            } else {
                                throw new RuntimeException("Unparseable date: " + rawDate);
                            }
                        } catch (Exception ex) {
                            anomaliesDetected.add(description + " - Unparseable date format: " + rawDate);
                            continue;
                        }
                    }

                    // Anomaly 1: Duplicates
                    String dedupeKey = parsedDate.toString() + "|" + paidBy.toLowerCase() + "|"
                            + amount.toPlainString();
                    if (processedRecords.contains(dedupeKey)) {
                        anomaliesDetected.add(description + " - Duplicate expense skipped.");
                        continue;
                    }
                    processedRecords.add(dedupeKey);

                    // Auto-Provision Ghost Users
                    User payer = resolveAndProvisionGhostUser(paidBy, group, successfulImports);

                    // Anomaly 4: Hidden Settlement
                    if (splitType.isEmpty()) {
                        anomaliesDetected
                                .add(description + " - Hidden Settlement: split_type is blank. Treated as Settlement.");
                        successfulImports.add(description + " [Imported as Settlement]");
                        continue;
                    }

                    // Standard Expense Import Success
                    SplitType parsedSplitType;
                    try {
                        parsedSplitType = SplitType.valueOf(splitType.toUpperCase());
                    } catch (Exception ex) {
                        parsedSplitType = SplitType.EQUAL; // fallback
                    }

                    // Parse split_with
                    Set<String> includedNames = new HashSet<>();
                    if (!splitWith.isEmpty()) {
                        for (String n : splitWith.split(";")) {
                            includedNames.add(n.trim().toLowerCase());
                        }
                    }

                    // Parse split_details
                    Map<String, BigDecimal> parsedAmounts = new HashMap<>();
                    if (!splitDetailsRaw.isEmpty()) {
                        for (String detail : splitDetailsRaw.split(";")) {
                            detail = detail.trim();
                            int lastSpace = detail.lastIndexOf(' ');
                            if (lastSpace != -1) {
                                String name = detail.substring(0, lastSpace).trim().toLowerCase();
                                String valStr = detail.substring(lastSpace + 1).replace("%", "").replace(",", "").trim();
                                try {
                                    parsedAmounts.put(name, new BigDecimal(valStr));
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    List<SplitDetails> splits = new ArrayList<>();
                    List<User> membersToSplit = new ArrayList<>();
                    
                    for (com.splitwise.entity.GroupMembership membership : group.getMemberships()) {
                        if (membership.getJoinedDate() != null && membership.getJoinedDate().isAfter(parsedDate)) continue;
                        if (membership.getLeftDate() != null && membership.getLeftDate().isBefore(parsedDate)) continue;

                        User member = membership.getUser();
                        if (member == null) continue;
                        String memberName = member.getName().toLowerCase();
                        if (includedNames.isEmpty() || includedNames.contains(memberName) || parsedAmounts.containsKey(memberName)) {
                            membersToSplit.add(member);
                        }
                    }

                    final LocalDate finalParsedDate1 = parsedDate;
                    if (membersToSplit.isEmpty()) {
                        membersToSplit = group.getMemberships().stream()
                                .filter(m -> m.getJoinedDate() != null && !m.getJoinedDate().isAfter(finalParsedDate1))
                                .filter(m -> m.getLeftDate() == null || !m.getLeftDate().isBefore(finalParsedDate1))
                                .map(com.splitwise.entity.GroupMembership::getUser)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList());
                    }

                    if (parsedSplitType == SplitType.PERCENTAGE) {
                        BigDecimal totalParsed = BigDecimal.ZERO;
                        for (User member : membersToSplit) {
                            String memberName = member.getName().toLowerCase();
                            if (parsedAmounts.containsKey(memberName)) {
                                totalParsed = totalParsed.add(parsedAmounts.get(memberName));
                            }
                        }

                        if (totalParsed.compareTo(BigDecimal.ZERO) == 0) {
                            // Fallback to even percentage distribution if missing
                            BigDecimal totalPercentage = new BigDecimal("100.00");
                            BigDecimal count = new BigDecimal(membersToSplit.size());
                            BigDecimal basePercentage = totalPercentage.divide(count, 2, RoundingMode.DOWN);
                            BigDecimal remainder = totalPercentage.subtract(basePercentage.multiply(count));

                            for (int i = 0; i < membersToSplit.size(); i++) {
                                BigDecimal percentage = basePercentage;
                                if (i == 0) {
                                    percentage = percentage.add(remainder);
                                }
                                splits.add(new SplitDetails(membersToSplit.get(i).getId(), percentage));
                            }
                        } else {
                            // Normalize the parsed amounts to sum exactly to 100.00
                            BigDecimal totalPercentage = new BigDecimal("100.00");
                            BigDecimal currentSum = BigDecimal.ZERO;

                            for (int i = 0; i < membersToSplit.size(); i++) {
                                User member = membersToSplit.get(i);
                                String memberName = member.getName().toLowerCase();
                                BigDecimal parsedAmount = parsedAmounts.getOrDefault(memberName, BigDecimal.ZERO);

                                // (parsedAmount / totalParsed) * 100
                                BigDecimal percentage = parsedAmount.multiply(totalPercentage)
                                        .divide(totalParsed, 2, RoundingMode.DOWN);

                                currentSum = currentSum.add(percentage);
                                splits.add(new SplitDetails(member.getId(), percentage));
                            }

                            // Give remainder to the first member to ensure absolute perfection
                            BigDecimal remainder = totalPercentage.subtract(currentSum);
                            if (remainder.compareTo(BigDecimal.ZERO) > 0 && !splits.isEmpty()) {
                                BigDecimal firstVal = splits.get(0).getValue();
                                splits.get(0).setValue(firstVal.add(remainder));
                            }
                        }
                    } else if (parsedSplitType == SplitType.EQUAL) {
                        for (User member : membersToSplit) {
                            splits.add(new SplitDetails(member.getId(), BigDecimal.ZERO));
                        }
                    } else {
                        for (User member : membersToSplit) {
                            String memberName = member.getName().toLowerCase();
                            if (parsedAmounts.containsKey(memberName)) {
                                splits.add(new SplitDetails(member.getId(), parsedAmounts.get(memberName)));
                            } else {
                                splits.add(new SplitDetails(member.getId(), BigDecimal.ZERO));
                            }
                        }
                    }

                    final LocalDate finalParsedDate2 = parsedDate;
                    if (splits.isEmpty()) {
                        parsedSplitType = SplitType.EQUAL;
                        splits = group.getMemberships().stream()
                            .filter(m -> m.getJoinedDate() != null && !m.getJoinedDate().isAfter(finalParsedDate2))
                            .filter(m -> m.getLeftDate() == null || !m.getLeftDate().isBefore(finalParsedDate2))
                            .map(com.splitwise.entity.GroupMembership::getUser)
                            .filter(java.util.Objects::nonNull)
                            .map(member -> new SplitDetails(member.getId(), BigDecimal.ZERO))
                            .collect(Collectors.toList());
                    }

                    // Pre-validate sums to avoid rolling back the transaction
                    if (parsedSplitType == SplitType.PERCENTAGE) {
                        BigDecimal totalPercentage = splits.stream()
                                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
                            anomaliesDetected.add(description + " - Percentages sum to " + totalPercentage + " instead of 100. Skipping row.");
                            continue;
                        }
                    } else if (parsedSplitType == SplitType.UNEQUAL) {
                        BigDecimal totalAmountSplit = splits.stream()
                                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (totalAmountSplit.compareTo(amount) != 0) {
                            anomaliesDetected.add(description + " - Unequal splits sum to " + totalAmountSplit + " instead of " + amount + ". Skipping row.");
                            continue;
                        }
                    }

                    if (splits.isEmpty()) {
                        anomaliesDetected.add(description + " - No active members in the group on " + parsedDate + ". Skipping row.");
                        continue;
                    }

                    CreateExpenseRequest request = new CreateExpenseRequest(
                            description,
                            amount,
                            payer.getId(),
                            parsedDate,
                            parsedSplitType,
                            splits);

                    expenseService.createExpense(groupId, request, principal);
                    successfulImports.add(description + " [Imported as Expense]");

                } catch (Exception ex) {
                    ex.printStackTrace(); // This forces the terminal to print the full red error!
                    anomaliesDetected.add("Row failed processing: " + ex.getMessage() + " | CAUSE: "
                            + (ex.getCause() != null ? ex.getCause().getMessage() : "Unknown"));
                }
            }
        }
        return new ImportReportResponse(totalProcessed, successfulImports, anomaliesDetected);
    }

    private User resolveAndProvisionGhostUser(String name, Group group, List<String> successfulImports) {
        User user = userRepository.findByNameIgnoreCase(name).orElse(null);
        if (user == null) {
            String email = name.toLowerCase().replaceAll("\\s+", "") + "@auto-import.local";
            user = User.builder()
                    .name(name)
                    .email(email)
                    .passwordHash(passwordEncoder.encode("ghost123"))
                    .build();
            user = userRepository.save(user);
            successfulImports.add("Auto-provisioned ghost user: " + name);
        }

        final User finalUser = user;
        boolean alreadyMember = group.getMemberships().stream()
                .anyMatch(m -> m.getUser().getId().equals(finalUser.getId()) && m.getLeftDate() == null);
        if (!alreadyMember) {
            group.getMemberships().add(new com.splitwise.entity.GroupMembership(group, user, group.getCreatedAt().toLocalDate()));
            groupRepository.save(group);
        }

        return user;
    }
}
