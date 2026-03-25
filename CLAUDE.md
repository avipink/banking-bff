# banking-bff

## Role in Product
Backend-for-Frontend service. Aggregates account and payment data from the core services into
frontend-optimized views. Stateless — owns no data. Single entry point for all frontend-facing
operations. Also contains a `legacy/` sub-package that deliberately demonstrates the DTO
duplication anti-pattern for training purposes; see below.

## Key Entry Points
| Class | Path | Methods |
|---|---|---|
| `DashboardController` | `/api/v1/dashboard` | `GET` — aggregates `AccountSummary` list + recent payments for first account |
| `DashboardController` | `/api/v1/dashboard/accounts/{id}` | `GET` — `AccountResponse` + full payment history for one account |
| `DashboardController` | `/api/v1/dashboard/transfer` | `POST` — forwards `PaymentRequest` to `payments-core-svc`; returns `PaymentResponse` |
| `LegacyDashboardController` | `/api/v1/legacy/dashboard` | `GET` — **anti-pattern demo only**; uses local duplicate DTOs; not for production use |

No Kafka consumers. No scheduled jobs.

## Domain Model
| Class | Kind | Notes |
|---|---|---|
| `DashboardView` | `data class` (view model) | `List<AccountSummary>` + `List<PaymentResponse>` + `totalAccountCount: Int` |
| `AccountDetailView` | `data class` (view model) | `AccountResponse` + `List<PaymentResponse>` |
| `AccountServiceClient` | `@Component` | WebClient adapter for `accounts-core-svc` |
| `PaymentServiceClient` | `@Component` | WebClient adapter for `payments-core-svc` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` | Maps `ResponseStatusException` and `WebClientResponseException` → `ApiError` |
| `LegacyAccountDto` | `data class` (anti-pattern) | Duplicates `AccountSummary` fields with type degradation — enums flattened to String |
| `LegacyDashboardResponse` | `data class` (anti-pattern) | Duplicates `PaginatedResponse` wrapper; `totalItems: Long` downcast to `count: Int` |

No BFF-owned data fields in the clean pattern — `DashboardView` and `AccountDetailView` are
pure structural wrappers composing unchanged contract types from `banking-contracts`.

## Depends On
- `banking-contracts` — Gradle composite build (`includeBuild("../banking-contracts")`)
  - Types used: `AccountSummary`, `AccountResponse`, `PaymentRequest`, `PaymentResponse`, `PaginatedResponse`, `ApiError`
- `accounts-core-svc` (:8081) — HTTP via `AccountServiceClient` (WebClient + `.block()`)
  - `GET /api/v1/accounts?page=1&pageSize=20` — hardcoded pagination defaults
  - `GET /api/v1/accounts/{id}`
  - Config: `accounts-service.base-url` (default: `http://localhost:8081`)
  - Failure mode: returns empty `PaginatedResponse` or null — **no error surfaced to frontend**
- `payments-core-svc` (:8082) — HTTP via `PaymentServiceClient` (WebClient + `.block()`)
  - `GET /api/v1/payments/account/{accountId}`
  - `POST /api/v1/payments`
  - Config: `payments-service.base-url` (default: `http://localhost:8082`)
  - **`submitPayment()` has no error handling** — `block()!!` propagates all upstream exceptions

All aggregation calls are **sequential** (not parallel). No `Mono.zip()`, no coroutines.

## Events Published and Consumed
None. No Kafka, no message broker, no scheduled jobs.

## Database
None. Stateless — no persistence of any kind.

## External Integrations
None. No third-party APIs.

## Known Complexity and Patterns
- **Sequential aggregation gap**: `GET /api/v1/dashboard` fetches payments for the **first account
  only** — other accounts' payment history is absent from the dashboard view.
- **Silent upstream failure**: `listAccounts()` and `getPaymentsByAccount()` swallow exceptions and
  return empty data — frontend receives HTTP 200 with empty payload, indistinguishable from a user
  with no accounts/payments.
- **Error context loss**: upstream `PaymentError` domain types (e.g., `DAILY_LIMIT_EXCEEDED`) are
  collapsed to `code: "UPSTREAM_ERROR"` by `GlobalExceptionHandler` — frontend cannot distinguish error causes.
- **Anti-pattern demo**: `legacy/` sub-package is intentional training material. `LegacyAccountDto`
  and `LegacyDashboardResponse` will drift silently from `banking-contracts` types on any contract change.
- No authentication, no caching, no rate limiting, no circuit breaker on any client.
