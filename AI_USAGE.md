# AI Usage Log

This document details the AI tools utilized during the development of this project, key prompts used, and concrete examples of where the AI made mistakes that required human intervention or architectural correction.

## 🛠️ Tools & Approach
- **AI Tool:** Google Gemini (Antigravity Agentic IDE Plugin)
- **Role:** Primary Development Collaborator
- **Key Prompts Used:**
  - *"Implement Rohan's Rule by building an AuditTrailResponse DTO and a specific @Query to isolate bilateral expenses between two users."*
  - *"We need to implement Meera's Rule. Re-architect the CsvImportService to support a stateless two-step approval flow."*
  - *"Scan the test-data CSV and identify every deliberate anomaly present in the file."*

## ⚠️ 3 Concrete AI Mistakes & Corrections

Working with an autonomous AI requires vigilant code review. Here are three specific instances where the AI generated flawed code, how I caught it, and exactly what we changed to fix it:

### 1. Transient Object Persistence Bug (`GroupService`)
**What the AI did wrong:** During Phase 1, the AI wrote the `createGroup()` method in a way that instantiated `GroupMembership` entities and attached them to a `Group` entity *before* calling `groupRepository.save(group)`. 
**How I caught it:** When testing group creation, the backend crashed with a `TransientPropertyValueException` leading to an `UnexpectedRollbackException`. The foreign key constraint was violated because the parent `Group` did not have a UUID yet.
**What I changed:** I directed the AI to re-order the persistence logic: we first call `groupRepository.save(group)` to generate the UUID, *then* loop through the members, instantiate the `GroupMembership` records, and save the group again.

### 2. Compilation Error in the Engine (`BalanceService`)
**What the AI did wrong:** While implementing Rohan's Audit Trail, the AI generated a Java stream mapping that called `split.getAmount()`. However, the actual property on the `ExpenseSplit` entity was named `amountOwed`.
**How I caught it:** The Maven `spring-boot:run` build completely failed during compilation with a "cannot find symbol" error pointing directly to the `BalanceService.java` file.
**What I changed:** I had the AI view the `ExpenseSplit.java` entity, realize the mismatch, and use a `multi_replace_file_content` block to correct the stream to `.map(ExpenseSplit::getAmountOwed)`.

### 3. Suboptimal Anomaly Resolution (Percentage Mismatches)
**What the AI did wrong:** When scanning the CSV, the AI found the "Pizza Friday" row where the percentages add up to 110%. The AI initially proposed code that would simply log an error message and *skip* the row entirely.
**How I caught it:** While reviewing the AI's proposed Implementation Plan for the 13 anomalies, I realized that skipping the row entirely violated the spirit of building a "smart" ledger. 
**What I changed:** I rejected the AI's initial approach and instructed it to rewrite the logic. The AI updated the code to mathematically *normalize* the percentages by dividing each share by the total (110) rather than 100, effectively re-weighting them down to exactly 100% and saving the expense data.
