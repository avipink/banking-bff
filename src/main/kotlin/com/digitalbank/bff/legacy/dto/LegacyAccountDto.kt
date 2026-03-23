package com.digitalbank.bff.legacy.dto

/**
 * ANTI-PATTERN DEMO: Locally duplicated account DTO.
 *
 * This class mirrors the fields of [com.digitalbank.contracts.accounts.AccountSummary]
 * but is defined independently in the BFF. This creates coupling-by-duplication:
 *
 * - When banking-contracts changes AccountSummary, this class must be updated manually.
 * - There is no compile-time guarantee that these two shapes stay in sync.
 * - Mapping code is required to convert between AccountSummary and LegacyAccountDto,
 *   adding maintenance overhead and a potential source of bugs.
 *
 * Compare with the clean pattern: [com.digitalbank.bff.clean.model.DashboardView]
 * which uses AccountSummary directly — no duplication, no mapping, no drift risk.
 */
data class LegacyAccountDto(
    // ⚠️ DUPLICATED from AccountSummary in banking-contracts
    val accountId: String,
    val accountType: String,   // String instead of enum — another anti-pattern
    val holderName: String,
    val balance: String,       // Flattened instead of MonetaryAmount — lossy
    val status: String,        // String instead of enum — no type safety
    val openedAt: String?      // ⚠️ Nullable String — manually synced from AccountSummary.openedAt
)
