# Ticket Reservation System

A production-grade ticket booking backend built with **Java 21**, **Spring Boot 3**, and **PostgreSQL 16**. Designed around the core problem of concurrent seat reservation — preventing double-selling under parallel load.

---

## Architecture

```
                      ┌──────────────────────────────────┐                                
                      │         Client / Next.js         │
                      └──────────────┬───────────────────┘
                                     │ HTTPS :443
                      ┌──────────────▼───────────────────┐
                    │         NGINX (load balancer)        │
                    │ 200r/s API · 20r/s auth · least_conn │
                    │JWT present? hash($http_authorization)│
                      └──────┬───────────────┬────────────┘
                             │               │
              ┌──────────────▼──┐     ┌──────▼──────────┐
              │  Instance 1     │     │  Instance 2     │
              │  Spring Boot    │     │  Spring Boot    │  ← --scale app=N
              │  app:8080       │     │  app:8080       │
              │  actuator:8081  │     │  actuator:8081  │
              │  Caffeine cache │     │  Caffeine cache │
              └──────┬──────────┘     └──────┬──────────┘
                     └──────────┬────────────┘
                      ┌─────────▼──────────────┐
                      │      PostgreSQL 16     │
                      │  row locks · Flyway    │
                      └────────────────────────┘
```
### Three-phase transactional checkout

Holding a database row lock open during a 10-second payment gateway call would serialise all concurrent checkouts. 
The flow is split into three phases:

```
TX 1: createPendingOrder()   — validate hold + discount, expire hold, create PENDING order
      ↓  (no transaction open — all DB connections and locks released)
      attemptPayment()       — call Stripe / PayPal (~2–10s)
      ↓
TX 2: finalizeOrder()        — persist payment result, update seat + order status
```

The hold is expired **atomically in TX 1**. Once a PENDING order exists for a hold token, 
replaying the same token always returns `HoldNotFoundException` — preventing double-charges on network retries.

---
---
## Tech Stack  
Language Java 21 Framework Spring Boot  
3.3 Database PostgreSQL 16 + Flyway ORM  
Spring Data JPA + Hibernate  
Security Spring Security + JWT   
Resilience Resilience4j(circuit breaker, retry, fallback)  
Scheduler coordination ShedLock  
Rate limiting (Bucket4j)  
Caffeine cache  
Metrics Micrometer + Prometheus   
Tracing Micrometer Tracing + OpenTelemetry → Grafana Tempo   
Reverse proxy NGINX (DNS upstream discovery, rate limiting, TLS)   
Load testing k6 API docs SpringDoc OpenAPI 3 / Swagger UI   
Containerisation Docker + Docker Compose   
Frontend Next.js 14 + TanStack Query + Tailwind CSS  

---

## Getting Started

### Prerequisites

- Docker Desktop
- k6 for load testing: `brew install k6` / `choco install k6`

### Start

### Configure
.env file
```
#.env:
#   JWT_SECRET — any 32+ character random string (required)
#   STRIPE_API_KEY — your stripe api key
#   GRAFANA_PASSWORD — change from default "admin"
```

```bash
# Full stack (NGINX + 2 app replicas + Prometheus + Tempo + Grafana)
docker compose up -d --build

# Scale app replicas — NGINX and Prometheus auto-discover new instances
docker compose up -d --scale app=4

```
## Load Testing

```bash
cd k6-tests

# Smoke test — 1 VU, full purchase flow, ~1 min
k6 run smoke-test.js

# 100 VUs browsing + 50 VUs doing full purchase flow
k6 run load-test.js

```

<img width="1417" height="921" alt="latest-load-test" src="https://github.com/user-attachments/assets/2c948729-a71d-45d2-803f-f7f0ee128aba" />


## API Reference

Full interactive docs with: `https://localhost/swagger-ui/index.html`

POST `/api/auth/register` — Register, returns JWT  
POST `/api/auth/login` — Login, returns JWT  
GET `/api/events` — List active events  
GET `/api/events/:id` — Event details  
GET `/api/events/:id/seats` — Live seating chart (polls every 15s in UI)  
POST `/api/holds` JWT Hold a seat (5 min TTL)  
DELETE `/api/holds/:token` JWT Release hold early  
POST `/api/orders/checkout` JWT Pay + confirm  
GET `/api/orders/me` JWT My order history  
GET `/api/orders/:id` JWT Single order  
POST `/api/admin/events` ADMIN Create event  
POST `/api/admin/seats` ADMIN Add seat to event  
POST `/api/admin/discounts` ADMIN Create discount code  
GET `/api/admin/discounts` ADMIN List all discount codes  
GET `/api/admin/orders` ADMIN All orders (all users)  
GET `/api/metrics/summary` ADMIN Live business metrics snapshot  
