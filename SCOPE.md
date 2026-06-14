# Scope & Anomaly Log

This document outlines the database schema driving the backend engine and logs all 13 deliberate anomalies discovered in the `expenses_export.csv` file, alongside their corresponding resolution policies.

---

## 🗄️ Database Schema

The core application uses a relational schema composed of 5 main entities:

1. **`users`**
   - `id` (UUID, Primary Key)
   - `name` (String, Non-Null)
   - `email` (String, Unique, Non-Null)
   - `password_hash` (String, Non-Null)

2. **`groups`**
   - `id` (UUID, Primary Key)
   - `name` (String, Non-Null)
   - `created_by` (UUID, Foreign Key -> `users.id`)
   - `created_at` (Timestamp)

3. **`group_members`** (Mapping table with membership dates for Sam's Rule)
   - `group_id` (UUID, Foreign Key -> `groups.id`)
   - `user_id` (UUID, Foreign Key -> `users.id`)
   - `joined_date` (Date)
   - `left_date` (Date, Nullable)
   - *Composite Primary Key: (`group_id`, `user_id`)*

4. **`expenses`** (Core ledger)
   - `id` (UUID, Primary Key)
   - `group_id` (UUID, Foreign Key -> `groups.id`)
   - `payer_id` (UUID, Foreign Key -> `users.id`)
   - `description` (String, Non-Null)
   - `amount` (Decimal, Non-Null)
   - `currency` (String, default 'INR')
   - `date` (Date, Non-Null)
   - `split_type` (Enum: EQUAL, UNEQUAL, PERCENTAGE, SHARE)

5. **`expense_splits`** (Individual debt assignments)
   - `id` (UUID, Primary Key)
   - `expense_id` (UUID, Foreign Key -> `expenses.id`)
   - `user_id` (UUID, Foreign Key -> `users.id`)
   - `amount_owed` (Decimal, Non-Null)

6. **`settlements`** (Debt resolution ledger)
   - `id` (UUID, Primary Key)
   - `group_id` (UUID, Foreign Key -> `groups.id`)
   - `payer_id` (UUID, Foreign Key -> `users.id`)
   - `payee_id` (UUID, Foreign Key -> `users.id`)
   - `amount` (Decimal, Non-Null)
   - `status` (Enum: PENDING, APPROVED, REJECTED)
   - `created_at` (Timestamp)

---

## 🔍 CSV Anomaly Log

During the ingestion of the provided `expenses_export.csv` file, 13 specific data anomalies were discovered. Meera's Rule ("Two-Step Approval") was implemented, meaning the system mathematically calculates the resolution of these anomalies in a stateless dry run before allowing the user to approve the actual database mutation.

Here is exactly how the 13 problems are handled:

| # | Anomaly / Edge Case | Example from CSV | Resolution Policy Implemented |
|---|---------------------|------------------|-------------------------------|
| **1** | **Exact Duplicates** | `dinner - marina bites` | **Skipped.** The system hashes the Date + Payer + Amount. If an identical hash is found in the current CSV, the second occurrence is rejected. |
| **2** | **Conflicting Duplicates** | `Thalassa dinner` logged twice (₹2400 & ₹2450) | **Higher Value Wins.** The code detects similar descriptions on the same day and intentionally ingests the higher amount while rejecting the lower conflicting entry. |
| **3** | **Percentage Sum Mismatch** | `Pizza Friday` (Sums to 110%) | **Auto-Normalized.** The percentages are mathematically re-weighted down to exactly 100% using `RoundingMode.DOWN`, distributing the remaining fractions to the first member to prevent rounding errors. |
| **4** | **Number Formatting** | `"1,200"` | **Sanitized.** Commas are automatically stripped before casting to BigDecimal. |
| **5** | **Missing Payer** | `House cleaning supplies` | **Skipped & Flagged.** A valid expense requires a creditor. The row is rejected. |
| **6** | **Hidden Settlement** | `Rohan paid Aisha back` | **Redirected.** A blank `split_type` forces the engine to treat this row as a direct settlement payment rather than a group expense. |
| **7** | **Foreign Currency** | `540 USD` (Goa villa booking) | **Converted.** Converted to INR using a strict multiplier of 95. |
| **8** | **Missing Currency** | `Groceries DMart` | **Defaulted.** Empty currency fields automatically default to INR. |
| **9** | **Negative Value** | `-30 USD` (Parasailing refund) | **Ingested as Refund.** Initially considered an error, the policy was updated to ingest negative amounts as valid reverse expenses. This perfectly and mathematically refunds the ledger balances. |
| **10** | **Zero Amount** | `0` (Swiggy dinner) | **Skipped.** Zero amounts do not mutate debts and are rejected as invalid. |
| **11** | **Bad Date Format 1** | `Mar-14` | **Standardized.** Converted to `14-03-2026` utilizing current-year fallback logic. |
| **12** | **Bad Date Format 2** | `04-05-2026` (Surrounded by April dates) | **Contextual Override.** Since surrounding rows are sequentially early April, the engine heuristically forces this inverted date to `05-04-2026`. |
| **13** | **Contradictory Splits** | `Furniture` (`equal` but provides shares) | **Strict Type Enforcement.** If `split_type` explicitly says EQUAL, the parser ignores provided share details and dynamically forces an equal split amongst active members. |
| **14** | **Former Member Inclusion** | `Groceries BigBasket` (Meera in April) | **Sam's Rule Enforced.** If an expense occurs after a member's `left_date` (or before their `joined_date`), they are automatically stripped from the split calculation, shielding them from the cost. |
| **15** | **Ghost Users** | Kabir | **Auto-Provisioned.** Unrecognized names are automatically persisted as ghost user accounts with generic emails to ensure ledger continuity. |
