# Distributed Job Queue & Worker System

A lightweight distributed job queue with worker processing, retries, DLQ routing, tenant limits, and a real-time dashboard UI. Built with Spring Boot and PostgreSQL.

---

## 🚀 Features

- **Job Submission API** (with idempotency key)
- **Worker Processing Loop** (lease → start → retry → complete → DLQ)
- **Per-Tenant Rate Limiting**
- **Dead Letter Queue Handling**
- **Event Logging** (submitted, leased, started, completed, failed, dlq)
- **Dashboard UI** (jobs table, summaries, event activity)
- **Tenant Filter + Global Summary**
- **Auto-Refresh Every 3 Seconds**
- **PostgreSQL Persistence**
- **Horizontally Scalable Worker Logic**

---

## 🏗️ Architecture Overview

┌─────────────┐      ┌──────────────────────┐
│   REST API  │─────▶│  PostgreSQL Storage  │
│  (Browser)  │◀─────│  (Port 8080)         │
└─────────────┘      └──────────────────────┘
        │                      ▲
        │                      │
        ▼                      │
┌──────────────────┐           │
│ Worker Scehduler │ ──────────┘
│ (Port 10000)     │     Process Jobs (leases/polls) 
└──────────────────┘
        │
job lifecycle events 
        │                      
        ▼
┌──────────────────┐
│   Dashboard UI   │
│    (polling)     │
└──────────────────┘

---

## 🧰 Tech Stack

- **Java 17**
- **Spring Boot 3**
- **Spring Data JPA**
- **Spring Scheduling**
- **PostgreSQL**
- **Docker Compose**
- **HTML + JavaScript UI**
- **Gradle**


---


## ⚙️ Local Setup
### 1️⃣ Start PostgreSQL

```
docker-compose up -d
```
This provides DB essentials,

- **DB: jobqueue_db**
- **Username: postgres**
- **Password: postgres**

--

### 2️⃣ Run Application
```
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
--
### 3️⃣ Access the Dashboard
```[http://localhost:8080/](http://localhost:8080/)```

---

## 🌐 API Endpoints

| Method |   Endpoint  | Description                        |
|--------|-----|------------------------------------|
| POST   |   /api/jobs  | Submit a new job                   |
| GET     |  /api/jobs   | List jobs (tenant filter optional) |
| GET     |  /api/jobs/{id}   | Get job details                    |
| GET       |/api/jobs/summary?tenantId=XYZ     | Tenant summary                     |
| GET       |/api/jobs/summary/global    | Global summary                     |


**Submit a job sample,**

```
POST /api/jobs
Header: X-Tenant-Id: demo-tenant

{
"payload": "{\"task\":\"sendEmail\"}",
"maxRetries": 3,
"idempotencyKey": "abc-123"
}
```

**Events API**

| Method |   Endpoint  | Description                        |
|--------|-----|------------------------------------|
| GET   |   /api/events  | Fetch the latest job lifecycle events   |

**Events include**,
```
SUBMITTED, LEASED, STARTED, COMPLETED, FAILED, DLQ
```
  
## 🧪 Useful API curl commands

---

**Submit a job**
```
curl -X POST http://localhost:8080/api/jobs \
-H "X-Tenant-Id: demo-tenant" \
-H "Content-Type: application/json" \
-d '{"payload":"{\"test\":true}", "maxRetries":2}'
```

**List jobs**
```
curl http://localhost:8080/api/jobs
```

**Get events**
```
curl http://localhost:8080/api/events
```

---

## ⚙️ System Parameters

| Parameter | Value |
|----------|--------|
| **Lease Duration** | 5 seconds |
| **Worker Tick Interval** | 5 seconds |
| **Max Retries Per Job** | 3 |
| **Batch Size** | 5 jobs per worker cycle |
| **Rate Limit** | 10 submissions/min per tenant |
| **Max Concurrent Jobs Per Tenant** | 5 (pending + running) |
| **Event Log Limit** | Latest 50 events |


## 🧩 System Components
### **API Server (`spring-boot` app)**
- Handles REST API requests
- Accepts job submissions
- Serves dashboard UI (`static/index.html`)
- Provides job summaries (global + tenant)
- Exposes events feed (`/api/events`)
- Optional: Exposes worker trigger (`/api/worker/run-once`)

### **Worker (inside same Spring Boot service)**
- Runs on a scheduled fixed delay
- Leases pending jobs using row-level DB locks
- Processes jobs (STARTED → COMPLETED or FAILED)
- Performs retries
- Moves jobs to DLQ after max attempts
- Emits job lifecycle events

### **PostgreSQL**
- Stores Jobs
- Stores Job Events
- Provides durable state across restarts

### **Dashboard (`index.html`)**
- Real-time job monitoring
- Tenant + global summaries
- Job list
- Recent events
- Tenant filter
- Manual & auto refresh
- JSON payload submission

---

## 🗄️ Database Schema

### **Jobs Table (`jobs`)**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key (auto-increment) |
| `tenant_id` | VARCHAR | Tenant identifier |
| `idempotency_key` | VARCHAR | Prevents duplicate job submission |
| `payload` | TEXT | Raw job payload (JSON string) |
| `status` | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `DLQ` |
| `attempt_count` | INT | Number of attempts made |
| `max_retries` | INT | Max retry attempts |
| `lease_until` | TIMESTAMP | Lease expiration timestamp |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last update time |
| `completed_at` | TIMESTAMP | Completion time |
| `last_error` | TEXT | Latest error message |

**Indexes**
- `(tenant_id, status)`
- Unique `(tenant_id, idempotency_key)`

--

### **Events Table (`job_events`)**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Event ID |
| `job_id` | BIGINT | Reference to job |
| `tenant_id` | VARCHAR | Tenant identifier |
| `event_type` | VARCHAR | `SUBMITTED`, `LEASED`, `STARTED`, `COMPLETED`, `FAILED`, `DLQ` |
| `message` | TEXT | Event message |
| `timestamp` | TIMESTAMP | Event timestamp |

**Index**
- `(timestamp DESC)`

---

## 🌀 Job Lifecycle Summary
```
SUBMITTED
   ↓
LEASED
   ↓
STARTED
   ↓
COMPLETED   → end
   ↓
FAILED      → retry (up to 3 times)
   ↓
  DLQ         (after max retries)
```
---

## 📁 Project Structure

---

src/
├── main/java/com/distributed/jobqueue
│     ├── controller       # REST APIs for jobs & events
│     ├── service          # Job service, worker, rate limiter, event logger
│     ├── repository       # Spring Data JPA repositories
│     ├── model            # Job, JobEvent, enums
│     └── dto              # Request/response payloads
└── main/resources
├── static/index.html       # Dashboard UI
├── application.yml         # Local config
└── application-render.yml  # Render deployment config

---
## 🎯 Deployment
The application is deployed on Render with -

* API server as a web service
* PostgreSQL as a managed shared database
