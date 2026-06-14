# AI_CONTEXT.md — Splitwise Clone

> **Status:** ✅ LOCKED — Ready for Implementation  
> **Last Updated:** 2026-06-13  
> **Timeline:** 3-day build  

---

## 1. Product Goals

- **Assignment objective:** Reverse-engineer Splitwise, scope a realistic 3-day version, build and deploy a working app.
- **Success criteria:**
  - Demonstrate strong software engineering principles and clean code architecture.
  - Deep understanding of the codebase (evaluator will quiz and request live feature modifications).
  - Fully functional, deployed app strictly adhering to requested features.
  - `AI_CONTEXT.md` must be accurate and complete enough that evaluators can recreate the app from it.
- **NOT a pixel-perfect clone** — engineering quality and correctness matter more than visual fidelity.

## 2. Splitwise Research

- Splitwise is a general-purpose expense-splitting app used by roommates, friends, travelers, coworkers.
- Core loop: create group → add expense → split among members → track balances → settle up.
- Key differentiators in real Splitwise (multi-currency, OCR, recurring expenses, categorization) are **out of scope** for this build.

## 3. User Personas

- **General-purpose users:** roommates splitting rent/utilities, friends splitting dinner or trip costs, coworkers splitting lunch.
- No specific niche — the app must work for any group-based expense-splitting scenario.

## 4. Core Workflows

1. User registers / logs in.
2. User creates a group and adds members.
3. User adds an expense to a group, choosing a split type (Equal, Unequal, Percentage, Share).
4. All group members see updated balances (who owes whom, how much).
5. Users chat in real-time within an expense view.
6. Users settle debts by recording payments.
7. Users view group-wise balances and an overall individual balance summary.

## 5. MVP Scope (Non-Negotiable Features)

| # | Feature | Detail |
|---|---------|--------|
| 1 | Authentication | JWT-based login/signup (BCrypt password hashing) |
| 2 | Groups | Create groups, manage membership (add/remove by email) |
| 3 | Expenses | Add expenses with 4 split types: Equal, Unequal, Percentage, Share |
| 4 | Real-time Chat | STOMP-over-WebSocket chat nested inside expense view |
| 5 | Balances | Group-wise balances + overall individual balance summary |
| 6 | Settlements | Record payments / settle debts (group-scoped, partial allowed) |
| 7 | Persistence | PostgreSQL with Hibernate ORM |
| 8 | Deployment | Backend+DB on Railway/Render, Frontend on Vercel, public GitHub repo |

## 6. Out-of-Scope Features

- Multiple currencies / currency conversion
- OCR receipt scanning
- Recurring expenses
- Expense categorization / tagging
- Push notifications
- Pixel-perfect Splitwise UI replication
- Multi-payer expenses
- Cross-group settlements
- Email verification flow
- API versioning
- Global activity feed
- Editing or deleting expenses/settlements (immutable ledger)
- Frontend automated tests
- Full integration/E2E test suites

---

## 7. Data Model

### 7.1 Users
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| email | VARCHAR | UNIQUE, NOT NULL |
| password_hash | VARCHAR | NOT NULL |
| name | VARCHAR | NOT NULL |

### 7.2 Groups
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| name | VARCHAR | NOT NULL |
| created_by | UUID | FK → Users(id), NOT NULL |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW |

### 7.3 Group Members _(join table)_
| Column | Type | Constraints |
|--------|------|-------------|
| group_id | UUID | FK → Groups(id), composite PK |
| user_id | UUID | FK → Users(id), composite PK |

### 7.4 Expenses
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| group_id | UUID | FK → Groups(id), NOT NULL |
| description | VARCHAR | NOT NULL |
| amount | DECIMAL(10,2) | NOT NULL |
| payer_id | UUID | FK → Users(id), NOT NULL |
| date | DATE | NOT NULL |
| split_type | ENUM | EQUAL, UNEQUAL, PERCENTAGE, SHARE — NOT NULL |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW |

- **Single payer per expense** (enforced).
- **Immutable** — no editing, no deleting.

### 7.5 Expense Splits
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| expense_id | UUID | FK → Expenses(id), NOT NULL |
| user_id | UUID | FK → Users(id), NOT NULL |
| amount_owed | DECIMAL(10,2) | NOT NULL |

### 7.6 Settlements
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| group_id | UUID | FK → Groups(id), NOT NULL |
| payer_id | UUID | FK → Users(id), NOT NULL |
| payee_id | UUID | FK → Users(id), NOT NULL |
| amount | DECIMAL(10,2) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW |

- **Group-scoped only** — no cross-group settlements.
- **Immutable** — no editing, no deleting.
- **Partial payments allowed.**

### 7.7 Chat Messages
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| expense_id | UUID | FK → Expenses(id), NOT NULL |
| sender_id | UUID | FK → Users(id), NOT NULL |
| text | TEXT | NOT NULL |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW |

- **Chat is nested inside an expense view**, not group-level.

### ER Diagram (Relationships)

```
Users ──┬──< Group_Members >──── Groups
        │                          │
        ├──< Expenses (payer) ─────┘
        │       │
        │       ├──< Expense_Splits
        │       └──< Chat_Messages
        │
        └──< Settlements (payer/payee) ── Groups
```

---

## 8. Authentication

- **Method:** JWT (JSON Web Tokens) — stateless, no server-side session storage.
- **Password hashing:** BCrypt via Spring Security's `BCryptPasswordEncoder`.
- **Flow:** Register (email + password + name) → Login (email + password) → receive JWT → attach to subsequent requests via `Authorization: Bearer <token>`.
- **Password rules:** Minimum 8 characters. No email verification for MVP.
- **Token storage (client):** `localStorage`.
- **WebSocket auth:** JWT passed as a header in the STOMP `CONNECT` frame → validated by a custom `ChannelInterceptor` → user principal bound to WebSocket session.
- **Known tradeoff:** `localStorage` is vulnerable to XSS attacks. Accepted for MVP velocity; documented as a known risk.

---

## 9. Groups & Expenses

### Groups
- Any authenticated user can create a group (they are auto-added as a member).
- Members added by **exact email** — user must already exist in the system.
- If email not found, UI shows "User not found" error.
- Removal only allowed if the user's net balance in that group is exactly $0.
- Removed users lose access but their historical expense split records are preserved (ledger integrity).

### Expenses
- Single payer per expense. Immutable after creation.
- 4 split types: EQUAL, UNEQUAL, PERCENTAGE, SHARE.
- **Split computation is server-side** — client sends `amount`, `split_type`, and split parameters; server calculates exact monetary splits, handles fractional rounding, and persists to `expense_splits`.

### Split Type Definitions
| Split Type | Client Sends | Server Computes |
|------------|-------------|-----------------|
| EQUAL | List of participant user IDs | `amount / N` per participant, remainder cent added to payer's share |
| UNEQUAL | Map of user_id → exact amount | Validates sum equals total, stores as-is |
| PERCENTAGE | Map of user_id → percentage | Validates percentages sum to 100, computes amounts, remainder to payer |
| SHARE | Map of user_id → share units | Computes proportional amounts from share ratios, remainder to payer |

### Rounding Strategy
- All splits computed using exact `BigDecimal` math to 2 decimal places with `RoundingMode.HALF_UP`.
- Any remaining fractional penny is added to the **payer's owed share**.
- Database entries always balance perfectly to the penny.

---

## 10. Settlements & Balances

### Settlements
- A settlement = a recorded payment from User A to User B within a group.
- Strictly group-scoped. Immutable.
- Partial payments are supported.

### Balance Algorithm
- **Approach:** Directed graph of net balances + greedy minimization algorithm using Max-Heaps.
- For each group, compute net balance per user: `(total they paid for others) − (total they owe to others)`, adjusted by settlements.
- Apply greedy algorithm using Max-Heaps (one for creditors, one for debtors) to minimize number of transactions needed to settle all debts.
- Expose both **group-wise balances** and an **overall individual balance summary** (aggregated across all groups).

### Settle Up UX Flow
1. User clicks "Settle Up" in group → modal opens.
2. Modal shows calculated bilateral debts (e.g., "You owe Bob $20").
3. Clicking "Settle" auto-populates: Payer = current user, Payee = creditor, Amount = full debt.
4. User can adjust amount for partial payment before confirming.

---

## 11. UI Screens & Routing

### Pages (Minimal Route Count)
| Route | Screen | Description |
|-------|--------|-------------|
| `/auth` | Auth Page | Login / Register toggle on a single page |
| `/dashboard` | Dashboard | Overall balance summary + list of groups with per-group net balances |
| `/groups/:id` | Group Detail | Group balances, member management, expense list, "Settle Up" button |
| `/expenses/:id` | Expense Detail | Split breakdown + real-time chat widget |

### Modals (No Separate Routes)
| Modal | Trigger Location | Purpose |
|-------|-----------------|---------|
| Create Group | Dashboard | Name input → create group |
| Add Member | Group Detail | Email input → add existing user |
| Add Expense | Group Detail | Dynamic React modal state management: Description, amount, split type dropdown, conditional rendering of split params → create expense |
| Settle Up | Group Detail | Shows debts, settle button, amount adjustment → record settlement |

---

## 12. Frontend Architecture

- **Framework:** React 18+ (via Vite)
- **Styling:** TailwindCSS v3
- **State management:** React `useState` + `useContext` (no external libraries)
- **JWT storage:** `localStorage`
- **HTTP client:** Axios (with request interceptors for auto-attaching JWT)
- **WebSocket client:** SockJS + STOMP.js for real-time chat
- **Routing:** React Router DOM

---

## 13. Backend Architecture

- **Language/Framework:** Java 21 / Spring Boot 3.x
- **Architecture pattern:** MVC (Controller → Service → Repository)
- **ORM:** Spring Data JPA / Hibernate
- **WebSocket:** STOMP over WebSocket (Spring WebSocket + Spring Messaging)
  - Custom `ChannelInterceptor` for JWT validation on STOMP CONNECT
- **Security:** Spring Security with JWT filter chain
- **Password hashing:** `BCryptPasswordEncoder`
- **Schema management:** Hibernate `ddl-auto=update` during development → `validate` in production
- **Validation:** Jakarta Bean Validation annotations

### Backend Package Structure
```
com.splitwise
├── config/          # SecurityConfig, WebSocketConfig, CorsConfig
├── controller/      # AuthController, GroupController, ExpenseController, SettlementController, ChatController
├── dto/             # Request/Response DTOs
├── entity/          # JPA Entities (User, Group, Expense, ExpenseSplit, Settlement, ChatMessage)
├── enums/           # SplitType enum
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
├── security/        # JwtTokenProvider, JwtAuthFilter, WebSocketAuthInterceptor
├── service/         # Business logic (SplitCalculationService, BalanceService, etc.)
└── SplitwiseApplication.java
```

---

## 14. Database Choice

- **Engine:** PostgreSQL (managed instance on Railway/Render)
- **Schema:** Auto-generated by Hibernate ORM from JPA entity definitions
- **DDL mode:** `update` during dev/initial deploy → `validate` once schema is locked
- **Constraints:** Standard relational (FKs, unique, not null, composite PKs)
- **Decimal precision:** `DECIMAL(10,2)` for all monetary fields

---

## 15. API Design

- **Style:** RESTful
- **Versioning:** None for MVP
- **Auth:** JWT Bearer token on all protected endpoints
- **Split computation:** Server-side only — client never computes financial amounts
- **Error responses:** Consistent JSON format `{ "error": "message" }` with appropriate HTTP status codes

### Endpoint Inventory

#### Auth (`/api/auth`) — Public
| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/auth/register` | `{ email, password, name }` | `{ id, email, name, token }` |
| POST | `/api/auth/login` | `{ email, password }` | `{ id, email, name, token }` |

#### Groups (`/api/groups`) — Protected
| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/groups` | `{ name }` | Group object |
| GET | `/api/groups` | — | List of user's groups with net balances |
| GET | `/api/groups/:id` | — | Group detail (members, balances) |
| POST | `/api/groups/:id/members` | `{ email }` | Updated member list |
| DELETE | `/api/groups/:id/members/:userId` | — | 200 OK (only if balance = $0) |

#### Expenses (`/api/groups/:id/expenses`) — Protected
| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/groups/:id/expenses` | `{ description, amount, payerId, date, splitType, splits: [...] }` | Expense with computed splits |
| GET | `/api/groups/:id/expenses` | — | List of expenses in group |
| GET | `/api/expenses/:id` | — | Expense detail with splits |

#### Settlements (`/api/groups/:id/settlements`) — Protected
| Method | Path | Request Body | Response |
|--------|------|-------------|----------|
| POST | `/api/groups/:id/settlements` | `{ payerId, payeeId, amount }` | Settlement object |
| GET | `/api/groups/:id/settlements` | — | List of settlements in group |

#### Balances — Protected
| Method | Path | Response |
|--------|------|----------|
| GET | `/api/groups/:id/balances` | Simplified debts for a group (minimized transactions) |
| GET | `/api/balances` | Overall balance summary for current user across all groups |

#### Chat (REST for history) — Protected
| Method | Path | Response |
|--------|------|----------|
| GET | `/api/expenses/:id/messages` | Chat message history for expense |

#### WebSocket (STOMP)
| Endpoint | Description |
|----------|-------------|
| `/ws` | STOMP WebSocket handshake endpoint (SockJS fallback) |
| `/topic/expenses/{expenseId}/chat` | Subscribe — receive live chat messages |
| `/app/expenses/{expenseId}/chat` | Publish — send a chat message |

---

## 16. Deployment

- **Architecture:** Decoupled — frontend and backend as separate services, communicating via CORS.
- **Backend + DB:** Spring Boot fat JAR + managed PostgreSQL deployed on **Railway** or **Render**.
- **Frontend:** React SPA deployed on **Vercel**.
- **CORS:** Backend explicitly whitelists the Vercel frontend domain.
- **Environment variables:** Database URL, JWT secret, CORS allowed origins — all configured via platform env vars.
- **Deliverables:** Public live URL + public GitHub repository (monorepo).

---

## 17. Testing

- **Strategy:** Focused unit tests on critical backend business logic + manual verification for everything else.
- **Unit tests (backend):**
  - `SplitCalculationService` — all 4 split types, rounding edge cases, validation errors.
  - `BalanceService` — greedy debt minimization algorithm correctness.
- **Manual verification:**
  - Full user workflow: register → login → create group → add members → add expenses → view balances → settle up → chat.
  - Edge cases: user not found on member add, removal with non-zero balance, partial settlement.
- **No frontend automated tests** — out of scope for 3-day timeline.
- **No integration/E2E tests** — out of scope for 3-day timeline.

---

## 18. Known Risks & Tradeoffs

| Risk | Mitigation |
|------|------------|
| `localStorage` JWT is vulnerable to XSS | Accepted for MVP. Document as known limitation. Upgrade to HTTP-only cookies post-MVP. |
| Hibernate `ddl-auto=update` in production | Start with `update` for speed; switch to `validate` once schema is stable. |
| WebSocket connections at scale | Not a concern for MVP — small user count expected. |
| No expense edit/delete | Users must create corrective expenses/settlements — documented as intentional immutable ledger design. |
| 3-day timeline pressure | Immutable records, no activity feed, modal-only forms — all reduce scope intentionally. |
| CORS misconfiguration | Must explicitly whitelist Vercel domain in Spring Security config. |
| No email verification | Users can register with any email. Accepted for MVP. |
| Floating-point rounding errors | All computation server-side using `BigDecimal`; remainder cents assigned to payer. |
| WebSocket auth bypass | Custom `ChannelInterceptor` validates JWT on every STOMP CONNECT frame. |

---

## 19. Project Structure (Monorepo)

```
splitwise-clone/
├── backend/                    # Spring Boot application
│   ├── src/main/java/com/splitwise/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── enums/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/
│   │   ├── service/
│   │   └── SplitwiseApplication.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── src/test/java/com/splitwise/
│   │   └── service/            # Unit tests for split calculation & balance logic
│   └── pom.xml
├── frontend/                   # React + Vite application
│   ├── src/
│   │   ├── components/         # Reusable UI components
│   │   ├── pages/              # Auth, Dashboard, GroupDetail, ExpenseDetail
│   │   ├── context/            # AuthContext, etc.
│   │   ├── services/           # Axios API client, WebSocket client
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── tailwind.config.js
│   ├── vite.config.js
│   └── package.json
├── AI_CONTEXT.md
├── BUILD_PLAN.md
└── README.md
```

---

_This document is the single source of truth for the Splitwise Clone project. It is complete and locked for implementation. Any changes during implementation will be reflected here._
