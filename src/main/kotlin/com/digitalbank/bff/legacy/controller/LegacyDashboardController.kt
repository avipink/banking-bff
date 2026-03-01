package com.digitalbank.bff.legacy.controller

import com.digitalbank.bff.clean.client.AccountServiceClient
import com.digitalbank.bff.legacy.dto.LegacyAccountDto
import com.digitalbank.bff.legacy.dto.LegacyDashboardResponse
import com.digitalbank.contracts.accounts.AccountSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ANTI-PATTERN DEMO: Legacy dashboard controller.
 *
 * Uses locally-duplicated DTOs ([LegacyDashboardResponse], [LegacyAccountDto])
 * instead of composing contract types from banking-contracts.
 *
 * The mapping function [toLegacyDto] below illustrates the mandatory translation
 * layer that the anti-pattern creates: every field must be manually copied,
 * and type mismatches (enum → String, MonetaryAmount → String) are introduced
 * at the mapping boundary, reducing type safety downstream.
 *
 * THIS IS A DELIBERATELY BAD EXAMPLE for training purposes.
 * Use [com.digitalbank.bff.clean.controller.DashboardController] as the reference.
 */
@RestController
@RequestMapping("/api/v1/legacy")
@Tag(name = "Dashboard (Legacy - Anti-Pattern)", description = "⚠️ Anti-pattern demo: uses duplicated local DTOs instead of shared contract types")
class LegacyDashboardController(
    private val accountClient: AccountServiceClient
) {

    @GetMapping("/dashboard")
    @Operation(
        summary = "Get legacy dashboard",
        description = "⚠️ ANTI-PATTERN: Returns locally-duplicated DTOs that mirror AccountSummary. " +
                "Any contract change in accounts-core-svc breaks this silently at runtime. " +
                "Compare with GET /api/v1/dashboard (clean pattern)."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Legacy dashboard retrieved (anti-pattern demo)")
    ])
    fun getLegacyDashboard(): LegacyDashboardResponse {
        val accounts = accountClient.listAccounts()
        val legacyAccounts = accounts.items.map { it.toLegacyDto() }
        return LegacyDashboardResponse(
            accounts = legacyAccounts,
            count = legacyAccounts.size  // ⚠️ Drops totalItems Long precision
        )
    }

    /**
     * ANTI-PATTERN: Manual mapping from contract type to duplicated local DTO.
     * This mapping exists only because of the duplication — the clean pattern
     * eliminates the need for this function entirely.
     */
    private fun AccountSummary.toLegacyDto() = LegacyAccountDto(
        accountId = accountId,
        accountType = accountType.name,   // ⚠️ Enum → String: loses type safety
        holderName = holderName,
        balance = "${balance.amount} ${balance.currency}",  // ⚠️ Flattens MonetaryAmount: lossy
        status = status.name              // ⚠️ Enum → String: loses type safety
    )
}
