# Splitwise Clone & CSV Ingestion Engine

A full-stack web application built to mirror the core functionality of Splitwise, featuring a powerful CSV ingestion engine capable of detecting, reporting, and elegantly handling malformed financial data.

## 🚀 Live Demo
- **Frontend:** https://expense-tracker-assignment-iota.vercel.app
- **Backend:** https://expense-tracker-assignment-l1hl.onrender.com

*(Note: The backend is hosted on a Render free tier instance. If it hasn't been used in a while, it may take 30-50 seconds to wake up upon your first request.)*

## 🛠️ Technology Stack
- **Frontend:** React, Vite, TailwindCSS, Axios
- **Backend:** Java 21, Spring Boot 3.3.5, Hibernate/JPA, Spring Security, WebSockets (STOMP)
- **Database:** PostgreSQL (Hosted on Supabase)
- **Deployment:** Vercel (Frontend), Render via Docker (Backend)

## ⚙️ Local Setup Instructions

### Prerequisites
- Java 21+ installed
- Node.js (v18+) installed
- PostgreSQL installed (or you can use the live Supabase URL already configured)

### 1. Database Configuration
By default, the application is configured to connect to the live remote Supabase database. If you wish to run it against a local PostgreSQL database, update the `backend/.env` file:
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/splitwise
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password
JWT_SECRET=super-secret-key-that-is-at-least-32-bytes-long-for-jwt
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

### 2. Running the Backend
1. Navigate to the `backend` directory:
   ```bash
   cd backend
   ```
2. Build and run using Maven:
   ```bash
   mvn clean install -DskipTests
   mvn spring-boot:run
   ```
   The backend will start on `http://localhost:8080`.

### 3. Running the Frontend
1. Navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Update `frontend/.env` (or create it if it doesn't exist) to point to your local backend:
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   ```
4. Start the Vite development server:
   ```bash
   npm run dev
   ```
   The frontend will be available at `http://localhost:5173`.

## 📄 Required Documentation
As per the assignment requirements, the following files are included in the root directory:
- `SCOPE.md` - The Anomaly Log and Database Schema
- `DECISIONS.md` - The Decision Log for architecture choices
- `Import_Report.md` - A sample output of the CSV import process showing anomalies handled
- `AI_USAGE.md` - Details of AI prompts, tools, and 4 specific mistakes the AI made and how they were corrected.
