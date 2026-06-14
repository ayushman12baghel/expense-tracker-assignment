# Decision Log

This document details the critical architectural and product decisions made while building this application. 

### 1. Two-Step CSV Approval Architecture (Meera's Rule)
**Options Considered:**
1. *Fully Stateful Backend:* The CSV is parsed and the changes are stored in a temporary database table `pending_imports`. The user reviews them via a frontend API call and approves them, which migrates the data to the real tables.
2. *Frontend Parsing:* The React app parses the CSV using `Papaparse`, displays the anomalies, and sends clean JSON payloads to the backend.
3. *Stateless Transaction Rollback (Chosen):* The backend executes a fully identical code path for parsing, calculating splits, and provisioning ghost users, but intentionally triggers a `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` at the very end.

**Why we chose it:** The stateless transaction rollback is the most robust and elegant solution. It leverages Spring's powerful `@Transactional` boundary to guarantee that the Preview report matches the actual import with 100% fidelity, because it literally executes the exact same SQL inserts and updates in memory before wiping them out. It requires zero temporary tables and ensures the backend remains the single source of truth for business logic.

---

### 2. Debt Simplification Algorithm (Aisha's Rule)
**Options Considered:**
1. *Naïve Ledger:* Everyone pays back exactly the person they borrowed from. (Results in 15+ different small transactions across a group of 5).
2. *Greedy Sink/Source Algorithm (Chosen):* Compute the net balance for every member. Separate them into Debtors (negative balance) and Creditors (positive balance). Iteratively settle the largest debt with the largest credit.

**Why we chose it:** It mathematically minimizes the total number of physical transactions required to settle the group. If Aisha owes Rohan $10, and Rohan owes Priya $10, Aisha's Rule demands "one number per person". The greedy algorithm simplifies this so Aisha just pays Priya $10 directly. 

---

### 3. Bilateral Audit Trails (Rohan's Rule)
**Options Considered:**
1. *Global History:* Just show a massive list of every expense the group ever made.
2. *Bilateral Querying (Chosen):* When Rohan clicks his ₹2,300 debt to Aisha, fire a specific `@Query` that isolates only the exact `Expense` and `Settlement` entities where Rohan and Aisha interacted.

**Why we chose it:** Global history violates the requirement "I want to see exactly which expenses make that up." By implementing a targeted Query, we guarantee Rohan isn't distracted by Priya and Dev's unrelated expenses. 

---

### 4. Date-Based Group Membership (Sam's Rule)
**Options Considered:**
1. *Soft Deletes:* Mark `is_active=false` on group members.
2. *Composite Tracking Key (Chosen):* Map `group_members` using `joined_date` and `left_date` timestamps.

**Why we chose it:** Soft deletes do not solve the problem of retroactively calculating balances for historical data. By strictly enforcing Date constraints, when an expense from March 1st is parsed, the backend checks the exact `joined_date` of all members. Since Sam joined mid-April, the `CsvImportService` naturally excludes him from the array of split candidates.

---

### 5. Negative Values in CSV
**Options Considered:**
1. *Throw an Error:* Reject any amount less than zero.
2. *Accept as Refund (Chosen):* Ingest the negative amount.

**Why we chose it:** The CSV contained `-30 USD` for "Parasailing refund". By ingesting a negative amount into an `EQUAL` split, the engine logically assigns a negative debt (credit) to everyone involved, perfectly reversing the cost of the canceled slot.
