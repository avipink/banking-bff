package com.digitalbank.bff.clean.controller

import com.digitalbank.bff.clean.client.AccountServiceClient
import com.digitalbank.bff.clean.client.PaymentServiceClient
import com.digitalbank.bff.clean.model.AccountDetailView
import com.digitalbank.bff.clean.model.DashboardView
import com.digitalbank.contracts.payments.PaymentRequest
import com.digitalbank.contracts.payments.PaymentResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * CLEAN PATTERN: BFF dashboard controller.
 *
 * Orchestrates calls to accounts-core-svc and payments-core-svc using contract
 * types from banking-contracts throughout. No local DTOs, no field duplication.
 *
 * Compare with [com.digitalbank.bff.legacy.controller.LegacyDashboardController]
 * which uses locally-duplicated DTOs (the anti-pattern this repo demonstrates).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard (Clean)", description = "BFF aggregation endpoints using shared contract types")
class DashboardController(
    private val accountClient: AccountServiceClient,
    private val paymentClient: PaymentServiceClient
) {

    @GetMapping
    @Operation(
        summary = "Get dashboard",
        description = "Aggregates account list and recent payments from Core services. " +
                "CLEAN PATTERN: all types sourced from banking-contracts."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully")
    ])
    fun getDashboard(): DashboardView {
        val accounts = accountClient.listAccounts()
        // Collect recent payments across all accounts (first page)
        val recentPayments = if (accounts.items.isNotEmpty()) {
            paymentClient.getPaymentsByAccount(accounts.items.first().accountId)
        } else {
            emptyList()
        }
        return DashboardView(
            accounts = accounts.items,
            recentPayments = recentPayments,
            totalAccountCount = accounts.totalItems.toInt()
        )
    }

    @GetMapping("/accounts/{id}")
    @Operation(
        summary = "Get account detail view",
        description = "Returns account details enriched with payment history. " +
                "CLEAN PATTERN: AccountResponse + List<PaymentResponse> from banking-contracts."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Account detail view retrieved"),
        ApiResponse(responseCode = "404", description = "Account not found")
    ])
    fun getAccountDetail(@PathVariable id: String): AccountDetailView {
        val account = accountClient.getAccount(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: $id")
        val payments = paymentClient.getPaymentsByAccount(id)
        return AccountDetailView(account = account, recentPayments = payments)
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Submit a transfer",
        description = "Forwards a PaymentRequest to payments-core-svc and returns the PaymentResponse. " +
                "CLEAN PATTERN: request and response types are banking-contracts types — zero BFF-owned DTOs."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Transfer submitted successfully"),
        ApiResponse(responseCode = "404", description = "Account not found"),
        ApiResponse(responseCode = "422", description = "Daily limit exceeded or validation failure")
    ])
    fun submitTransfer(@RequestBody request: PaymentRequest): PaymentResponse =
        paymentClient.submitPayment(request)
}
