package com.digitalbank.bff.clean.model

import com.digitalbank.contracts.accounts.AccountSummary
import com.digitalbank.contracts.payments.PaymentResponse

/**
 * BFF view model for the dashboard endpoint.
 *
 * CLEAN PATTERN: Composes contract types from banking-contracts directly.
 * No duplication — account and payment data shapes are defined once in the
 * shared contracts library and reused here.
 *
 * Compare with [com.digitalbank.bff.legacy.dto.LegacyDashboardResponse]
 * which duplicates these shapes as local DTOs (anti-pattern).
 */
data class DashboardView(
    val accounts: List<AccountSummary>,
    val recentPayments: List<PaymentResponse>,
    val totalAccountCount: Int
)
