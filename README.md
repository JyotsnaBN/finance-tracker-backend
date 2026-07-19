# Finance Tracker — Backend

This is the main backend service for the Finance Tracker system. While the Android app just acts as a thin frontend client, this Spring Boot service handles the core logic. It automatically processes bank text messages and emails, filters out duplicates, tracks analytics and builds user reports.

---

## What it does

When a bank SMS arrives on your phone, the Android app captures the raw text and sends it here. The backend parses the message for the amount, transaction type, date and account number before saving it to the database. It does the same for transaction emails, except those are pulled automatically from Gmail every hour via a GitHub Actions cron job. No manual intervention needed.

Beyond parsing, the backend handles user auth, account management, category-based spend tracking and report delivery over email.

---

## Tech Stack

- **Java 17** with Spring Boot 3.2.x
- **PostgreSQL** via Supabase
- **Spring Security** — JWT authentication, BCrypt password hashing
- **Gmail API v1** — multi-user OAuth, tokens encrypted at rest with AES-256-GCM
- **Lombok + Maven**
- **GitHub Actions** — cron job that triggers email processing hourly

---

## Key Features

### Authentication
- Register and login with JWT tokens
- Refresh token support
- BCrypt password hashing
- All endpoints require Bearer JWT except `/api/auth/**`, the OAuth callback and the internal email-processing trigger

### SMS Parsing
- Comprehensive regex engine that extracts amount, transaction type (debit/credit), date, account hint, merchant/description and available credit limit
- Handles edge cases: missing dates, future dates, unknown merchants, malformed amounts
- Shared by both the SMS ingestion path and the email reader — one parser, two consumers
- Failed parses are queued for manual review rather than silently dropped

### Email Reading
- Connects to Gmail via OAuth 2.0
- Reads bank transaction emails automatically, every hour
- Uses the same `SmsParserService` to extract transaction data from email bodies
- Detects delivery confirmation emails separately — extracts order items, tracking numbers, merchant and delivery date, then links them to the matching financial transaction

### Auto-Categorization
- Keyword-based category matching across 10+ categories and 100+ keywords
- Categories include: Food, Transport, Shopping, Utilities, Healthcare, Entertainment and more

### Duplicate Detection
Three layers:
1. Raw text match (exact same SMS/email body)
2. Exact field match (amount + date + account)
3. 5-minute similarity window (catches near-duplicates from split SMS messages)

### Analytics
- Dashboard summary: current vs last month income, expenses, savings rate, top spending categories, recent transactions
- Spending trends by week, month, quarter and year

### Reports
- Summary, income vs expense, category breakdown and account balance reports
- Delivered via email — not stored in-app

### Account Management
- Supports Savings, Current, Credit Card and Wallet account types
- Multi-level account resolution: matches transactions to accounts by last 4 digits → bank + type → type only

---

## Project Structure

```
backend/src/main/java/com/financetracker/
├── controller/         # REST API endpoints (11 controllers)
├── service/
│   ├── SmsParserService.java       # Core regex parser (SMS + email)
│   ├── EmailReaderService.java     # Gmail API integration
│   ├── AnalyticsService.java       # Dashboard + trends
│   ├── TransactionService.java     # CRUD + duplicate detection
│   ├── DuplicateDetectionService.java
│   ├── ReportService.java
│   └── GoogleOAuthService.java
├── util/
│   ├── AmountExtractor.java        # 4-tier amount pattern matching
│   ├── DeliveryEmailParser.java    # Delivery email detection + parsing
│   ├── EncryptionUtil.java         # AES-256-GCM for OAuth tokens
│   └── TransactionParsingUtil.java
├── model/              # JPA entities
├── dto/                # Request/response objects
├── repository/         # Spring Data JPA
├── security/           # JWT filter + token provider
└── config/             # Security + OAuth configuration
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Create account, returns JWT |
| `POST` | `/api/auth/login` | Login, returns JWT |
| `POST` | `/api/transactions` | Create a single transaction |
| `POST` | `/api/transactions/bulk` | Bulk create (used by SyncWorker) |
| `GET`  | `/api/analytics/dashboard` | Income/expense/savings summary |
| `GET`  | `/api/analytics/trends` | Spending trends over time |
| `GET`  | `/api/reports/summary` | Date-range report |
| `POST` | `/api/email/connect` | Initiate Gmail OAuth |
| `GET`  | `/api/email/status` | List connected email configs |
| `GET`  | `/api/failed-transactions` | View parse failures |
| `POST` | `/api/internal/email-processing` | Cron trigger (X-API-KEY auth) |

---

## Getting Started

**Prerequisites:**
- Java 17+
- Maven
- Supabase account (free tier works fine)
- Google Cloud project with Gmail API enabled
- IntelliJ IDEA or any Java IDE


**Environment variables you'll need:**
```
SUPABASE_URL
SUPABASE_DB_PASSWORD
JWT_SECRET
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
ENCRYPTION_KEY          # AES-256-GCM key for OAuth token storage
INTERNAL_API_KEY        # Used by the GitHub Actions cron trigger
```
Full reference: [`.env.template`](.env.template)


**Run:**
```bash
mvn spring-boot:run
```


---


## What's Coming Next

- **AI-powered categorization** — replacing the static keyword list with a model that actually understands transaction descriptions. "UBER* TRIP" → Transport. "SWGY*ORDER" → Food. No more manually maintaining a 100-keyword CSV. The idea is to use a lightweight classification model so the category assignment gets smarter the more transactions flow through.
- Rate limiting on public endpoints
- Comprehensive API endpoint documentation

