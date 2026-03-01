package com.digitalbank.bff.clean.model

import com.digitalbank.contracts.accounts.AccountResponse
import com.digitalbank.contracts.payments.PaymentResponse

/**
 * BFF view model for the account detail endpoint.
 *
 * CLEAN PATTERN: Wraps the [AccountResponse] contract type and enriches it
 * with the account's payment history, both sourced from their respective
 * Core services.
 *
 * No field duplication — all shapes owned by banking-contracts.
 */
data class AccountDetailView(
    val account: AccountResponse,
    val recentPayments: List<PaymentResponse>
)
