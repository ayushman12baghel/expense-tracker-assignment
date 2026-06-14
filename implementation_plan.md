# CSV Import Engine & Documentation Delivery Plan

This plan covers the implementation of the new CSV Import feature to handle the 8 specific data anomalies, as well as the generation of the newly required documentation files (`SCOPE.md` and `DECISIONS.md`).

## User Review Required
> [!IMPORTANT]
> Please review the anomaly handling logic detailed in the Proposed Changes below. Specifically, confirm if my proposed resolutions for "Duplicates" (rejecting the second occurrence in the batch) and "Negative Value" (rejecting the record entirely) align with the evaluator's expectations.

## Open Questions
> [!WARNING]
> 1. **Duplicates:** Should the duplicate "dinner - marina bites" be completely skipped and added to `anomaliesDetected`, or should it be merged/added anyway? (I propose rejecting it and adding it to the anomaly report).
> 2. **Negative Values:** Should the "-30" (Parasailing refund) be rejected as an anomaly, or should we ingest it as a reverse settlement? (I propose rejecting it as an anomaly).
> 3. **Date Format:** When resolving "Mar-14", should we assume the current year (e.g. 14-03-2026)?

## Proposed Changes

### Backend Dependencies
#### [MODIFY] [pom.xml](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/backend/pom.xml)
- Add `com.opencsv:opencsv` dependency to parse the multipart CSV file seamlessly.

---

### DTOs
#### [NEW] [ImportReportResponse.java](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/backend/src/main/java/com/splitwise/dto/ImportReportResponse.java)
- Will contain:
  - `List<String> successfulImports`
  - `List<String> anomaliesDetected`
  - `int totalProcessed`

---

### Business Logic (The CSV Engine)
#### [NEW] [CsvImportService.java](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/backend/src/main/java/com/splitwise/service/CsvImportService.java)
A robust parsing engine that will read the `MultipartFile` using `opencsv` and apply the following anomaly resolution rules:
1. **Duplicates:** Track normalized descriptions (lowercase, trimmed). If a match is found in the current batch, log to `anomaliesDetected` and skip.
2. **Number Formatting:** Strip all commas before parsing `BigDecimal`.
3. **Missing Payer:** If `paid_by` is blank, skip record and add to `anomaliesDetected`.
4. **Hidden Settlement:** If `split_type` is blank, route the record to the `SettlementService` instead of creating an expense.
5. **Foreign Currency:** If currency is `USD`, multiply by `95` using `BigDecimal.multiply()`.
6. **Missing Currency:** If currency is blank, default to `INR`.
7. **Negative Value:** If amount < 0, skip record and add to `anomaliesDetected`.
8. **Bad Date Format:** Use a fallback `DateTimeFormatter` that catches `MMM-dd` (e.g., Mar-14) and appends the current year to convert to `DD-MM-YYYY`.

#### [NEW] [CsvImportController.java](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/backend/src/main/java/com/splitwise/controller/CsvImportController.java)
- Expose `POST /api/expenses/import`
- Accepts `@RequestParam("file") MultipartFile file`
- Returns `ResponseEntity<ImportReportResponse>`

---

### Documentation Artifacts
#### [NEW] [SCOPE.md](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/SCOPE.md)
Will detail the Anomaly Log (the 8 edge cases discovered in the CSV) and outline the DB schema constraints used to mitigate bad data (e.g., `DECIMAL(10,2)`, non-null payer constraints).

#### [NEW] [DECISIONS.md](file:///c:/Users/ayush/OneDrive/Desktop/Splitwise%20assignment/DECISIONS.md)
Will detail the significant technical decisions we've made throughout the sprint:
- Using `BigDecimal` for financial precision.
- The Max-Heap Greedy Minimization graph algorithm.
- WebSockets with STOMP & JWT ChannelInterceptor.
- Single monolith backend vs microservices for MVP velocity.

## Verification Plan
### Automated Tests
- I will not write a full unit test for the CSV importer unless requested, but I will write the service defensively to ensure it compiles and executes flawlessly.
### Manual Verification
- The user can test `POST /api/expenses/import` via Postman by uploading their `test.csv` file and observing the JSON `ImportReportResponse`.
