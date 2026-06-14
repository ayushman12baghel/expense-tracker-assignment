# BUILD_PLAN.md — Splitwise Clone

> **Generated:** 2026-06-13  
> **Source of Truth:** [AI_CONTEXT.md](./AI_CONTEXT.md)  
> **Timeline:** 3 days  

---

## Day 1 — Foundation & Core Backend

### Phase 1.1: Project Initialization (1–2 hours)

- [ ] Initialize Git monorepo with `/backend` and `/frontend` directories
- [ ] **Backend:** Generate Spring Boot 3.x project (Java 21, Maven)
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-websocket`, `spring-boot-starter-validation`, `postgresql`, `jjwt`, `lombok`
  - Configure `application.properties`: datasource (PostgreSQL), Hibernate `ddl-auto=update`, JWT secret, server port
- [ ] **Frontend:** Scaffold React app via Vite
  - Install: `react-router-dom`, `axios`, `@stomp/stompjs`, `sockjs-client`
  - Install & configure TailwindCSS v3
- [ ] Verify both projects build and run locally
- [ ] Push initial commit

### Phase 1.2: JPA Entities & Database Schema (1–2 hours)

- [ ] Create JPA entities:
  - `User` (id UUID, email, passwordHash, name)
  - `Group` (id UUID, name, createdBy FK, createdAt) with `@ManyToMany` members via join table `group_members`
  - `Expense` (id UUID, groupId FK, description, amount DECIMAL(10,2), payerId FK, date, splitType ENUM, createdAt)
  - `ExpenseSplit` (id UUID, expenseId FK, userId FK, amountOwed DECIMAL(10,2))
  - `Settlement` (id UUID, groupId FK, payerId FK, payeeId FK, amount DECIMAL(10,2), createdAt)
  - `ChatMessage` (id UUID, expenseId FK, senderId FK, text, createdAt)
- [ ] Create `SplitType` enum: `EQUAL, UNEQUAL, PERCENTAGE, SHARE`
- [ ] Create Spring Data JPA repositories for all entities
- [ ] Start PostgreSQL locally, verify Hibernate auto-generates schema
- [ ] Verify all FK constraints and composite keys are correct

### Phase 1.3: Authentication Module (2–3 hours)

- [ ] Create DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`
- [ ] Implement `JwtTokenProvider` (generate, validate, extract claims)
- [ ] Implement `JwtAuthFilter` (extends `OncePerRequestFilter`, reads Bearer token, sets `SecurityContext`)
- [ ] Configure `SecurityFilterChain`:
  - Public: `/api/auth/**`, `/ws/**`
  - Protected: everything else
  - Stateless session management
  - CORS configuration (allow Vercel origin)
- [ ] Implement `AuthController`:
  - `POST /api/auth/register` → validate, hash password (BCrypt), save user, return JWT
  - `POST /api/auth/login` → authenticate, return JWT
- [ ] Implement `UserService` with registration & login logic
- [ ] Create `GlobalExceptionHandler` with consistent error response format `{ "error": "message" }`
- [ ] Test auth endpoints with Postman/curl

### Phase 1.4: Group CRUD (2 hours)

- [ ] Create DTOs: `CreateGroupRequest`, `GroupResponse`, `AddMemberRequest`
- [ ] Implement `GroupService`:
  - Create group (auto-add creator as member)
  - List user's groups
  - Get group detail with members
  - Add member by email (validate user exists)
  - Remove member (validate balance = $0)
- [ ] Implement `GroupController`:
  - `POST /api/groups`
  - `GET /api/groups`
  - `GET /api/groups/:id`
  - `POST /api/groups/:id/members`
  - `DELETE /api/groups/:id/members/:userId`
- [ ] Authorization: verify requesting user is a member of the group for all group endpoints
- [ ] Test group endpoints

---

## Day 2 — Business Logic & Frontend

### Phase 2.1: Expense & Split Calculation Engine (3–4 hours)

- [ ] Create DTOs: `CreateExpenseRequest`, `ExpenseResponse`, `ExpenseSplitResponse`
- [ ] Implement `SplitCalculationService` — **THE CRITICAL ENGINE**:
  - `calculateEqual(amount, participantIds)` → divide evenly, assign remainder cent to payer
  - `calculateUnequal(amount, splits)` → validate sum equals total
  - `calculatePercentage(amount, percentages)` → validate sum = 100%, compute amounts, remainder to payer
  - `calculateShare(amount, shares)` → compute proportional, remainder to payer
  - All math uses `BigDecimal` with `RoundingMode.HALF_UP`, scale 2
- [ ] Implement `ExpenseService`:
  - Create expense: validate group membership, validate payer is member, delegate to `SplitCalculationService`, persist expense + splits in a single transaction
  - Get expense detail with splits
  - List expenses for a group
- [ ] Implement `ExpenseController`:
  - `POST /api/groups/:id/expenses`
  - `GET /api/groups/:id/expenses`
  - `GET /api/expenses/:id`
- [ ] **Write unit tests:**
  - `SplitCalculationServiceTest` — all 4 split types, edge cases (odd divisions, single participant, large amounts, zero-amount splits), validation failures
- [ ] Test expense endpoints

### Phase 2.2: Settlements & Balance Algorithm (2–3 hours)

- [ ] Create DTOs: `CreateSettlementRequest`, `SettlementResponse`, `BalanceResponse`, `DebtResponse`
- [ ] Implement `BalanceService` — **GREEDY MINIMIZATION**:
  - For a group: query all expenses (with splits) and settlements
  - Compute net balance per user: `net = (total paid as payer across expenses) − (total owed across expense_splits) + (total paid in settlements) − (total received in settlements)`
  - Apply greedy algorithm: sort users by net balance, repeatedly match largest creditor with largest debtor
  - Return simplified list of `{ from, to, amount }` debts
  - For overall user balance: aggregate across all groups
- [ ] Implement `SettlementService`:
  - Create settlement: validate group membership, validate payer ≠ payee, persist
  - List settlements for a group
- [ ] Implement `SettlementController`:
  - `POST /api/groups/:id/settlements`
  - `GET /api/groups/:id/settlements`
- [ ] Implement `BalanceController`:
  - `GET /api/groups/:id/balances`
  - `GET /api/balances`
- [ ] **Write unit tests:**
  - `BalanceServiceTest` — greedy algorithm correctness, 3-person cycle, already-settled debts, mixed expenses + settlements
- [ ] Test balance & settlement endpoints

### Phase 2.3: Frontend Foundation (2–3 hours)

- [ ] Set up project structure: `/pages`, `/components`, `/context`, `/services`
- [ ] Implement `AuthContext` (stores JWT in localStorage, exposes login/logout/register/currentUser)
- [ ] Implement `api.js` Axios instance with JWT interceptor
- [ ] Implement React Router:
  - `/auth` → AuthPage
  - `/dashboard` → Dashboard (protected)
  - `/groups/:id` → GroupDetail (protected)
  - `/expenses/:id` → ExpenseDetail (protected)
  - Redirect unauthenticated users to `/auth`
- [ ] Build `AuthPage`:
  - Login form (email, password)
  - Register form (name, email, password)
  - Toggle between login/register
- [ ] Verify login → JWT stored → redirected to dashboard

---

## Day 3 — Frontend Completion, Chat, Polish & Deploy

### Phase 3.1: Dashboard & Group Detail Pages (2–3 hours)

- [ ] Build `Dashboard`:
  - Overall balance summary ("You owe $X" / "You are owed $Y")
  - List of groups with per-group net balance
  - "Create Group" button → modal
- [ ] Build `CreateGroupModal` (name input → POST `/api/groups`)
- [ ] Build `GroupDetail`:
  - Group name & member list with "Add Member" button
  - Group balances widget (simplified debts from `/api/groups/:id/balances`)
  - Expense list (from `/api/groups/:id/expenses`)
  - "Add Expense" button → modal
  - "Settle Up" button → modal
- [ ] Build `AddMemberModal` (email input → POST `/api/groups/:id/members`, error on "User not found")
- [ ] Build `AddExpenseModal`:
  - Description, amount, split type selector
  - Dynamic form: EQUAL shows checkboxes, UNEQUAL/PERCENTAGE/SHARE shows input per member
  - Submits to `POST /api/groups/:id/expenses`
- [ ] Build `SettleUpModal`:
  - Displays calculated bilateral debts
  - "Settle" button auto-populates payer/payee/amount
  - Adjustable amount for partial payments
  - Submits to `POST /api/groups/:id/settlements`

### Phase 3.2: Expense Detail & Real-Time Chat (2–3 hours)

- [ ] Build `ExpenseDetail`:
  - Expense metadata (description, amount, payer, date, split type)
  - Split breakdown table (who owes what)
- [ ] Implement WebSocket chat:
  - **Backend:** Configure `WebSocketMessageBrokerConfigurer` (STOMP endpoint `/ws`, topic prefix `/topic`, app prefix `/app`)
  - **Backend:** Implement `WebSocketAuthInterceptor` (ChannelInterceptor, validates JWT from STOMP CONNECT headers, sets user principal)
  - **Backend:** Implement `ChatController` (message broker):
    - `@MessageMapping("/expenses/{expenseId}/chat")` → save to DB, broadcast to `/topic/expenses/{expenseId}/chat`
    - `GET /api/expenses/:id/messages` → REST endpoint for chat history
  - **Frontend:** Build `ChatWidget` component:
    - On mount: fetch history via REST, connect STOMP client, subscribe to topic
    - Send message via STOMP publish
    - Display messages in real-time (auto-scroll)
    - On unmount: disconnect STOMP client
- [ ] Verify real-time chat works between two browser windows

### Phase 3.3: Polish & Edge Cases (1–2 hours)

- [ ] Loading states and error handling on all API calls
- [ ] Input validation on all forms (match backend rules)
- [ ] Protected route redirects (unauthenticated → `/auth`)
- [ ] Verify edge cases:
  - Member removal blocked when balance ≠ $0
  - "User not found" error on member add
  - Rounding correctness (e.g., $100 split 3 ways)
  - Partial settlements
- [ ] Responsive layout (mobile-friendly)
- [ ] Clean up console errors, unused imports, dead code

### Phase 3.4: Deployment (1–2 hours)

- [ ] **Backend deployment (Railway/Render):**
  - Create managed PostgreSQL instance
  - Deploy Spring Boot JAR
  - Set environment variables: `DATABASE_URL`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`
  - Verify health check endpoint
- [ ] **Frontend deployment (Vercel):**
  - Configure build command (`npm run build`) and output dir (`dist`)
  - Set environment variable: `VITE_API_BASE_URL` pointing to backend URL
  - Deploy
- [ ] **CORS verification:** confirm frontend can call backend from deployed URL
- [ ] **WebSocket verification:** confirm chat works on deployed version
- [ ] **Smoke test full workflow on production:**
  1. Register two users
  2. Create a group
  3. Add second user to group
  4. Create expense (test all 4 split types)
  5. Verify balances
  6. Settle up (full + partial)
  7. Chat on an expense
- [ ] **Final deliverables:**
  - [ ] Public GitHub repo URL
  - [ ] Live deployed app URL
  - [ ] Updated AI_CONTEXT.md committed to repo
  - [ ] Updated BUILD_PLAN.md committed to repo
  - [ ] README.md with setup instructions, live URL, and feature list

---

## Risk Checkpoints

| Checkpoint | When | Action if Behind |
|------------|------|-----------------|
| Auth + Groups + Entities done | End of Day 1 | Cut member removal feature, hardcode group membership |
| Expenses + Balances + Frontend auth working | End of Day 2 | Simplify balance to raw debts (skip greedy minimization), reduce to 2 split types |
| Chat + Deploy done | Mid Day 3 | Deploy without chat, add chat post-deploy |

---

## Quick Reference: Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18+ (Vite), TailwindCSS v3, React Router, Axios, STOMP.js + SockJS |
| Backend | Java 21, Spring Boot 3.x, Spring Security, Spring WebSocket |
| Database | PostgreSQL, Hibernate/JPA |
| Auth | JWT (jjwt), BCrypt |
| Deployment | Railway/Render (backend + DB), Vercel (frontend) |
| Testing | JUnit 5 (unit tests on split calculation + balance algorithm) |
| Repo | Single GitHub monorepo (`/backend`, `/frontend`) |

---

_This plan is derived entirely from AI_CONTEXT.md. Execute in order. Update AI_CONTEXT.md if any decisions change during implementation._
