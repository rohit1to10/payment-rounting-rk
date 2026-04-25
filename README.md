# Dynamic Payment Gateway Routing Service

A Spring Boot 3 / Java 21 service that intelligently routes payment transactions across multiple gateways (Razorpay, PayU, Cashfree) using adaptive scoring, circuit breakers, and real-time health monitoring — all in-memory, zero external dependencies.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Routing Logic](#routing-logic)
3. [Circuit Breaker Design](#circuit-breaker-design)
4. [Project Structure](#project-structure)
5. [Running Locally](#running-locally)
6. [Running with Docker](#running-with-docker)
7. [API Reference](#api-reference)
8. [Demo Scenarios](#demo-scenarios)
9. [Running Tests](#running-tests)
10. [Design Decisions](#design-decisions)

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│  REST Layer                                                │
│  POST /transactions/initiate   POST /transactions/callback │
│  GET  /gateways/stats          POST /gateways/{gw}/...     │
└────────────────┬───────────────────────────────────────────┘
                 │
     ┌───────────▼──────────────┐
     │    TransactionService     │
     └──────┬──────────┬────────┘
            │          │
  ┌─────────▼──┐  ┌────▼──────────────┐
  │ Routing    │  │ GatewayHealth     │
  │ Engine     │  │ Service           │
  │            │  │                   │
  │ score =    │  │ • Sliding window  │
  │ weight*0.4 │  │ • Circuit breaker │
  │ +success*04│  │ • Adaptive weight │
  │ -failure*02│  └────────┬──────────┘
  └─────────┬──┘           │
            │         ┌────▼──────────┐
            │         │ GatewayStats  │  ← In-memory ConcurrentHashMap
            │         │ Store         │
            └────────►│               │
                      └───────────────┘

  Mock Gateway Layer               Callback Parser Layer (Strategy Pattern)
  ┌──────────────────┐             ┌──────────────────────────────────────┐
  │ RazorpayClient   │             │ RazorpayParser  "captured" → success │
  │ PayUClient       │             │ PayUParser      "success"  → success │
  │ CashfreeClient   │             │ CashfreeParser  "SUCCESS"  → success │
  └──────────────────┘             └──────────────────────────────────────┘

  Scheduler (every 60s)
  ┌──────────────────────────────────────────┐
  │ OPEN → HALF_OPEN  when cooldown expires  │
  │ Purge stale sliding-window events        │
  │ Log health snapshot every 2 min          │
  └──────────────────────────────────────────┘
```

---

## Routing Logic

Every `POST /transactions/initiate` call runs through three steps:

### Step 1 — Filter
- **CLOSED** gateways are fully eligible.
- **HALF_OPEN** gateways are included with `halfOpenTrafficPercent` (5%) probability for recovery probing.
- **OPEN** (unhealthy) gateways are excluded. If **all** gateways are OPEN, the least-bad one (highest dynamic weight) is used as a fallback.

### Step 2 — Score
Each eligible gateway receives a composite score:

```
score = (normalizedWeight × 0.4)
      + (successRate      × 0.4)
      - (failureRate      × 0.2)
```

Where:
- `normalizedWeight` = gateway's current dynamic weight ÷ sum of all candidates' weights
- `successRate` / `failureRate` = from the **last 15-minute sliding window**
- A **retry penalty of −0.15** is applied to gateways already tried for the same `order_id`

### Step 3 — Select
The gateway with the highest score is selected. The routing decision is logged with all scores and the reason.

### Step 4 — Adaptive Weights
After each callback:
- **Success** → weight += 2 (capped at 150% of base)
- **Failure** → weight -= 3 (floored at 50% of base)

This self-adjusts traffic distribution toward better-performing gateways over time.

---

## Circuit Breaker Design

```
         ┌──────────────────────────────────┐
         │  Success rate < 90% in 15 min    │
CLOSED ──►                                  ├──► OPEN ──► (30 min cooldown)
         │  (with ≥ 5 samples)              │               │
         └──────────────────────────────────┘               │ cooldown expires
                                                            ▼
                                                        HALF_OPEN
                                                  (5% traffic probing)
                                                     │           │
                                           stable?  ▼           ▼  still bad?
                                                  CLOSED       OPEN (reset cooldown)
```

| State | Behaviour |
|---|---|
| `CLOSED` | Full traffic. Monitors success rate. |
| `OPEN` | Blocked for 30 minutes. No traffic. |
| `HALF_OPEN` | 5% of traffic allowed to probe recovery. |

---

## Project Structure

```
payment-routing/
├── src/main/java/com/platinumrx/paymentrouting/
│   ├── PaymentRoutingApplication.java
│   ├── config/
│   │   └── GatewayConfig.java           # @ConfigurationProperties: weights, thresholds
│   ├── model/
│   │   ├── GatewayState.java            # CLOSED / OPEN / HALF_OPEN
│   │   ├── TransactionStatus.java       # PENDING / SUCCESS / FAILURE
│   │   ├── Transaction.java             # Mutable domain model
│   │   ├── GatewayStats.java            # Thread-safe in-memory gateway state
│   │   └── PaymentInstrument.java
│   ├── dto/
│   │   ├── InitiateRequest.java         # POST /transactions/initiate payload
│   │   ├── InitiateResponse.java        # Response with routingMetadata
│   │   ├── CallbackRequest.java         # POST /transactions/callback payload
│   │   ├── RoutingMetadata.java         # Scores, reason, fallbacks
│   │   └── GatewayStatsResponse.java
│   ├── store/
│   │   ├── TransactionStore.java        # ConcurrentHashMap by txId + orderId index
│   │   └── GatewayStatsStore.java       # Initialised from config via @PostConstruct
│   ├── routing/
│   │   ├── RoutingEngine.java           # Core scoring + selection logic
│   │   └── RoutingResult.java
│   ├── gateway/                         # Mock gateway layer
│   │   ├── MockGatewayClient.java       # Interface
│   │   ├── RazorpayGatewayClient.java
│   │   ├── PayUGatewayClient.java
│   │   └── CashfreeGatewayClient.java
│   ├── callback/                        # Strategy pattern for callback parsing
│   │   ├── CallbackParser.java          # Interface
│   │   ├── ParsedCallback.java
│   │   ├── RazorpayCallbackParser.java  # "captured" → success
│   │   ├── PayUCallbackParser.java      # "success" → success
│   │   └── CashfreeCallbackParser.java  # "SUCCESS" → success
│   ├── service/
│   │   ├── TransactionService.java      # Orchestrates initiate + callback flows
│   │   └── GatewayHealthService.java    # Sliding window + circuit breaker logic
│   ├── scheduler/
│   │   └── GatewayHealthScheduler.java  # Cooldown checks + snapshot logging
│   └── controller/
│       ├── TransactionController.java
│       ├── GatewayController.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.yml                  # Gateway weights + simulation config
├── src/test/java/com/platinumrx/paymentrouting/
│   ├── routing/RoutingEngineTest.java
│   ├── service/GatewayHealthServiceTest.java
│   ├── service/TransactionServiceTest.java
│   ├── callback/CallbackParserTest.java
│   └── integration/PaymentRoutingIntegrationTest.java
├── Dockerfile
├── docker-compose.yml
└── .dockerignore
```

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.9+

```bash
cd payment-routing

# Build
mvn package -DskipTests

# Run
java -jar target/payment-routing-1.0.0.jar
```

The service starts on **port 8080**.

### Configuration

All settings live in `src/main/resources/application.yml`:

```yaml
gateway:
  health:
    window-minutes: 15          # Sliding window for success rate calculation
    threshold: 0.90             # Min success rate before gateway goes OPEN
    cooldown-minutes: 30        # How long an OPEN gateway is blocked
    min-samples: 5              # Min transactions before health evaluation starts
    half-open-traffic-percent: 5 # % of traffic allowed during recovery probe

  gateways:
    razorpay:
      weight: 40                # Base routing weight
      failure-rate: 10          # Simulated failure rate (%)
      latency-ms: 100           # Simulated latency
    payu:
      weight: 35
      failure-rate: 15
      latency-ms: 150
    cashfree:
      weight: 25
      failure-rate: 5
      latency-ms: 80
```

Override any setting via environment variable at runtime:
```bash
GATEWAY_GATEWAYS_RAZORPAY_FAILURE-RATE=50 java -jar target/payment-routing-1.0.0.jar
```

---

## Running with Docker

### Build and run
```bash
docker-compose up --build
```

### Run pre-built image
```bash
docker build -t payment-routing:latest .
docker run -p 8080:8080 payment-routing:latest
```

### Override config at runtime
```bash
docker run -p 8080:8080 \
  -e GATEWAY_GATEWAYS_RAZORPAY_FAILURE-RATE=30 \
  payment-routing:latest
```

### Stop
```bash
docker-compose down
```

---

## API Reference

### POST /transactions/initiate

Initiates a payment transaction. The routing engine selects the best available gateway.

**Request**
```json
{
  "order_id": "ORD123",
  "amount": 499.0,
  "payment_instrument": {
    "type": "card",
    "card_number": "****",
    "expiry": "12/26"
  },
  "idempotency_key": "optional-key"
}
```

**Response**
```json
{
  "transactionId": "b3f1a2c4-...",
  "orderId": "ORD123",
  "gateway": "razorpay",
  "status": "PENDING",
  "attemptNumber": 1,
  "timestamp": "2026-04-24T10:00:00Z",
  "routingMetadata": {
    "score": 0.560,
    "reason": "Highest composite score (weight+success-failure); success_rate=100.0%",
    "availableGateways": ["razorpay", "payu", "cashfree"],
    "fallbackCandidates": ["payu", "cashfree"],
    "gatewayScores": {
      "razorpay": 0.560,
      "payu": 0.540,
      "cashfree": 0.500
    }
  }
}
```

---

### POST /transactions/callback

Records the payment outcome from a gateway. Updates transaction status and triggers health re-evaluation.

Each gateway uses different status values (handled transparently by the parser strategy):

| Gateway | Success values | Failure values |
|---|---|---|
| Razorpay | `captured`, `success` | `failed`, `refunded`, … |
| PayU | `success` | `failure`, `userCancelled`, … |
| Cashfree | `SUCCESS` | `FAILED`, `USER_DROPPED`, … |

**Request**
```json
{
  "order_id": "ORD123",
  "status": "captured",
  "gateway": "razorpay",
  "reason": "Customer Cancelled"
}
```

**Response**
```json
{
  "status": "processed",
  "transaction_id": "b3f1a2c4-...",
  "outcome": "SUCCESS"
}
```

---

### GET /gateways/stats

Returns live health and performance stats for all gateways.

**Response**
```json
[
  {
    "gatewayName": "razorpay",
    "state": "CLOSED",
    "dynamicWeight": 42.0,
    "recentSuccessCount": 8,
    "recentFailureCount": 2,
    "recentSuccessRate": 0.80,
    "totalSuccess": 120,
    "totalFailure": 15,
    "cooldownUntil": null
  }
]
```

---

### POST /gateways/{gateway}/inject-failures?count=N

Injects N synthetic failures into a gateway for demo/testing. Useful for triggering health degradation and observing failover behaviour.

```bash
curl -X POST "http://localhost:8080/gateways/razorpay/inject-failures?count=10"
```

---

### POST /gateways/{gateway}/force-unhealthy

Immediately marks a gateway as OPEN (blocked) with a 30-minute cooldown.

```bash
curl -X POST http://localhost:8080/gateways/razorpay/force-unhealthy
```

---

### POST /gateways/{gateway}/reset

Resets a gateway back to CLOSED (healthy) state and restores its base weight.

```bash
curl -X POST http://localhost:8080/gateways/razorpay/reset
```

---

### GET /transactions

Returns all transactions. Also supports:
- `GET /transactions/{transactionId}` — single transaction
- `GET /transactions/order/{orderId}` — all attempts for an order

---

## Demo Scenarios

### Scenario 1: Normal Routing

```bash
# Send 5 transactions and observe weighted distribution
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/transactions/initiate \
    -H "Content-Type: application/json" \
    -d "{\"order_id\":\"ORD-$i\",\"amount\":100,\"payment_instrument\":{\"type\":\"card\"}}" \
    | python3 -m json.tool | grep '"gateway"'
done
```

You'll see traffic weighted roughly 40/35/25 across Razorpay/PayU/Cashfree.

---

### Scenario 2: Failover (Circuit Breaker Demo)

```bash
# Step 1 — Check initial state
curl -s http://localhost:8080/gateways/stats | python3 -m json.tool

# Step 2 — Inject 10 failures into Razorpay (drops success rate to ~9%)
curl -X POST "http://localhost:8080/gateways/razorpay/inject-failures?count=10"

# Step 3 — Razorpay is now OPEN
curl -s http://localhost:8080/gateways/stats | python3 -m json.tool | grep -A3 '"razorpay"'

# Step 4 — New transactions avoid Razorpay
curl -s -X POST http://localhost:8080/transactions/initiate \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-FAILOVER","amount":500,"payment_instrument":{"type":"upi","vpa":"user@upi"}}' \
  | python3 -m json.tool | grep '"gateway"'

# Step 5 — Restore Razorpay
curl -X POST http://localhost:8080/gateways/razorpay/reset
```

---

### Scenario 3: Multi-Attempt Order (Retry Diversification)

```bash
# Make first attempt — note the gateway
curl -s -X POST http://localhost:8080/transactions/initiate \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-RETRY","amount":200,"payment_instrument":{"type":"card"}}' \
  | python3 -m json.tool | grep -E '"gateway"|"attemptNumber"'

# Retry the same order — routing engine penalises the first gateway
curl -s -X POST http://localhost:8080/transactions/initiate \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-RETRY","amount":200,"payment_instrument":{"type":"card"}}' \
  | python3 -m json.tool | grep -E '"gateway"|"attemptNumber"'
```

---

### Scenario 4: All Gateways Unhealthy (Least-Bad Fallback)

```bash
# Force all gateways unhealthy
curl -X POST http://localhost:8080/gateways/razorpay/force-unhealthy
curl -X POST http://localhost:8080/gateways/payu/force-unhealthy
curl -X POST http://localhost:8080/gateways/cashfree/force-unhealthy

# Service uses least-bad (highest dynamic weight) gateway instead of failing
curl -s -X POST http://localhost:8080/transactions/initiate \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-FALLBACK","amount":99,"payment_instrument":{"type":"card"}}' \
  | python3 -m json.tool | grep '"fallbackUsed\|gateway"'
```

---

### Scenario 5: Callback — Razorpay Success vs PayU Failure

```bash
# Razorpay uses "captured" for success
curl -X POST http://localhost:8080/transactions/callback \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-CB1","status":"captured","gateway":"razorpay"}'

# Cashfree uses uppercase "SUCCESS"
curl -X POST http://localhost:8080/transactions/callback \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-CB2","status":"SUCCESS","gateway":"cashfree"}'

# PayU failure with reason
curl -X POST http://localhost:8080/transactions/callback \
  -H "Content-Type: application/json" \
  -d '{"order_id":"ORD-CB3","status":"userCancelled","gateway":"payu","reason":"Customer Cancelled"}'
```

---

## Running Tests

```bash
# All tests
mvn test

# Unit tests only (fast, no Spring context)
mvn test -pl . -Dtest="RoutingEngineTest,GatewayHealthServiceTest,TransactionServiceTest,CallbackParserTest"

# Integration tests only
mvn test -Dtest="PaymentRoutingIntegrationTest"
```

### Test Coverage

| Test Class | Type | What it covers |
|---|---|---|
| `RoutingEngineTest` | Unit | Scoring, OPEN exclusion, fallback, retry penalty, HALF_OPEN probing |
| `GatewayHealthServiceTest` | Unit | Circuit breaker transitions, adaptive weights |
| `TransactionServiceTest` | Unit | Initiate flow, callback flow, multi-attempt, retry diversification |
| `CallbackParserTest` | Unit | Razorpay/PayU/Cashfree status mappings (parameterised) |
| `PaymentRoutingIntegrationTest` | Integration | Full REST API end-to-end: all endpoints, failover, health degradation |

---

## Design Decisions

### Why in-memory?
The assignment calls for in-memory storage. `ConcurrentHashMap` for transactions and `ConcurrentLinkedDeque<Instant>` for sliding windows give thread-safe, O(1) access without external dependencies.
The store classes are behind interfaces — swapping to Redis or a relational DB only requires new `Store` implementations.

### Why no Lombok?
Lombok annotation processing is unreliable on Homebrew JDK builds (Java 21+). The code uses explicit builders and getters throughout instead — identical semantics, no tooling friction.

### Callback strategy pattern
Each gateway uses different status vocabulary (`captured` vs `success` vs `SUCCESS`). The `CallbackParser` interface lets us add a new gateway by adding one class without touching the service layer.

### Sliding window vs fixed bucket
The window uses a `Deque<Instant>` rather than a pre-bucketed counter. This means success rate is always exact for the last 15 minutes, with no granularity loss. The scheduler purges stale entries every 5 minutes to bound memory.

### Adaptive weights vs static weights
Static weights determine the baseline. Dynamic weights adapt ±2/±3 per callback, bounded to [50%, 150%] of base. This means a gateway that starts handling more traffic and performing well earns more; one degrading loses share gradually rather than abruptly.
