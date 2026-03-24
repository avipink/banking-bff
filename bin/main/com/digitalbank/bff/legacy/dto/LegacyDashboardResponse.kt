package com.digitalbank.bff.legacy.dto

/**
 * ANTI-PATTERN DEMO: Locally duplicated dashboard response DTO.
 *
 * Wraps a list of [LegacyAccountDto] — another locally-owned type that mirrors
 * contract types without reusing them.
 *
 * Problems with this approach:
 * 1. The total count field is `Int` here vs `Long` in PaginatedResponse — a silent
 *    overflow risk for large datasets.
 * 2. Any schema change in accounts-core-svc breaks this silently at runtime
 *    rather than at compile time.
 * 3. Adds a mapping layer with no business value.
 *
 * The clean alternative is [com.digitalbank.bff.clean.model.DashboardView].
 */
data class LegacyDashboardResponse(
    // ⚠️ DUPLICATED structure — should use PaginatedResponse<AccountSummary>
    val accounts: List<LegacyAccountDto>,
    val count: Int   // ⚠️ Should be Long to match PaginatedResponse.totalItems
)
