package com.digitalbank.bff.clean.client

import com.digitalbank.contracts.accounts.AccountResponse
import com.digitalbank.contracts.accounts.AccountSummary
import com.digitalbank.contracts.common.PaginatedResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * HTTP client for accounts-core-svc (port 8081).
 *
 * CLEAN PATTERN: All return types are contract types from banking-contracts.
 * The BFF owns no account data model — it delegates entirely to accounts-core-svc
 * and passes contract types through to callers.
 */
@Component
class AccountServiceClient(
    @Value("\${accounts-service.base-url}") baseUrl: String
) {
    private val log = LoggerFactory.getLogger(AccountServiceClient::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun listAccounts(page: Int = 1, pageSize: Int = 20): PaginatedResponse<AccountSummary> {
        return try {
            webClient.get()
                .uri { it.path("/api/v1/accounts").queryParam("page", page).queryParam("pageSize", pageSize).build() }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<PaginatedResponse<AccountSummary>>() {})
                .block() ?: PaginatedResponse(emptyList(), page, pageSize, 0L, 0)
        } catch (ex: Exception) {
            log.error("Failed to list accounts: {}", ex.message)
            PaginatedResponse(emptyList(), page, pageSize, 0L, 0)
        }
    }

    fun getAccount(accountId: String): AccountResponse? {
        return try {
            webClient.get()
                .uri("/api/v1/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(AccountResponse::class.java)
                .block()
        } catch (ex: WebClientResponseException.NotFound) {
            null
        } catch (ex: Exception) {
            log.error("Failed to get account {}: {}", accountId, ex.message)
            null
        }
    }
}
