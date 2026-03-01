# banking-bff — Code Generation Plan

## Unit: banking-bff (single unit)

### Files to Generate

- [x] **Step 1**: `settings.gradle.kts` — composite build referencing banking-contracts
- [x] **Step 2**: `build.gradle.kts` — Spring Boot 3.3.5 + webflux + springdoc 2.6.0
- [x] **Step 3**: `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.5
- [x] **Step 4**: `src/main/resources/application.yml` — port 8080, upstream URLs
- [x] **Step 5**: `BffApplication.kt` — Spring Boot entry point
- [x] **Step 6**: `clean/model/DashboardView.kt` — composes AccountSummary + PaymentResponse
- [x] **Step 7**: `clean/model/AccountDetailView.kt` — composes AccountResponse + payments
- [x] **Step 8**: `clean/client/AccountServiceClient.kt` — WebClient for accounts-core-svc
- [x] **Step 9**: `clean/client/PaymentServiceClient.kt` — WebClient for payments-core-svc
- [x] **Step 10**: `clean/controller/DashboardController.kt` — 3 endpoints, clean contract types
- [x] **Step 11**: `legacy/dto/LegacyAccountDto.kt` — duplicated DTO (anti-pattern demo)
- [x] **Step 12**: `legacy/dto/LegacyDashboardResponse.kt` — duplicated wrapper (anti-pattern demo)
- [x] **Step 13**: `legacy/controller/LegacyDashboardController.kt` — legacy endpoint with mapping
- [x] **Step 14**: `exception/GlobalExceptionHandler.kt` — upstream + status exception mapping

## Status: COMPLETE ✅
