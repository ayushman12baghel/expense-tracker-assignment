# Splitwise Clone — A Full-Stack MVP

**🌐 Live Demo:** [https://expense-tracker-assignment-iota.vercel.app/](https://expense-tracker-assignment-iota.vercel.app/)

Welcome to the **Splitwise Clone**! This project is a robust, production-ready Minimum Viable Product (MVP) engineered over a 3-day sprint. It replicates the core financial and social features of Splitwise, allowing users to create groups, add multi-party expenses with complex split logic, settle debts using a greedy minimization algorithm, and converse via real-time WebSocket chat.

## 🚀 Core Features

- **Advanced Expense Splits**: Create expenses with mathematically precise splitting algorithms (`BigDecimal` with `RoundingMode.HALF_UP`). Supports:
  - **Equal**: Automatic division with remainder pennies assigned to the payer.
  - **Unequal**: Exact monetary amounts per user.
  - **Percentage**: Precise percentage-based allocations.
  - **Shares**: Proportional unit-based splits.
- **Greedy Minimization Balance Algorithm**: A graph-based math engine utilizing Max-Heaps to instantly reduce complex, overlapping group debts into the absolute minimum number of settlement transactions.
- **Real-Time Group Chat**: A WebSocket layer powered by STOMP and SockJS, allowing users to converse in real-time within specific expense threads.
- **Stateless Authentication**: Fully secured with JWT (JSON Web Tokens) acting as Bearer tokens, including a custom `ChannelInterceptor` to firmly secure WebSocket STOMP handshakes.
- **Dynamic UX State Management**: A highly polished, reactive frontend built with React and Tailwind CSS, featuring glassmorphism aesthetics and complex dynamic modal handling.

## 🛠️ Tech Stack

### Backend
- **Java 21**
- **Spring Boot 3.3.5**
- **PostgreSQL** (with Hibernate ORM / Spring Data JPA)
- **Spring Security & JJWT** (Stateless Authentication)
- **Spring WebSocket / STOMP** (Real-Time Messaging)
- **JUnit 5 & Mockito** (Unit Testing)

### Frontend
- **React 18** (via Vite)
- **TailwindCSS v3** (Utility-first styling)
- **React Router DOM** (Client-side routing)
- **Axios** (HTTP Client with Interceptors)
- **@stomp/stompjs & sockjs-client** (WebSocket Client)

---

## ⚙️ Local Setup Instructions

### Prerequisites
- Java 21+
- Node.js 18+
- Maven
- PostgreSQL running locally

### 1. Database Configuration
Create a local PostgreSQL database (e.g., `splitwise_db`).

### 2. Backend Setup
1. Open a terminal and navigate to the `backend` directory:
   ```bash
   cd backend
   ```
2. Set up your environment variables by copying the example file:
   ```bash
   cp .env.example .env
   ```
3. Open `.env` and configure your `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `JWT_SECRET`.
4. Run the Spring Boot application (ensure your environment variables are loaded, or use an IDE plugin like EnvFile):
   ```bash
   # If using bash with a local .env file:
   set -o allexport; source .env; set +o allexport; mvn spring-boot:run
   ```
   *The backend will start on `http://localhost:8080`. Hibernate will automatically generate the database schema.*

### 3. Frontend Setup
1. Open a new terminal and navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```
2. Set up your environment variables by copying the example file:
   ```bash
   cp .env.example .env
   ```
   *(The defaults in `.env.example` point to `http://localhost:8080` for local development).*
3. Install the dependencies:
   ```bash
   npm install
   ```
4. Start the Vite development server:
   ```bash
   npm run dev
   ```
   *The frontend will start on `http://localhost:5173`.*

---

## 🤖 AI Collaboration Note

This application was engineered via a highly structured pair-programming session with an AI development partner (Anthropic's Claude/Antigravity). 

The collaboration was governed strictly by a centralized source-of-truth document: [`AI_CONTEXT.md`](./AI_CONTEXT.md). Every architectural decision, schema design, algorithmic approach, and UI pattern was planned, documented, and locked in `AI_CONTEXT.md` before a single line of code was written. This document serves as a blueprint capable of allowing any developer (or AI agent) to flawlessly recreate this system from scratch.
