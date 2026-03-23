package com.digitalbank.bff.clean.client

import com.digitalbank.contracts.payments.PaymentRequest
import com.digitalbank.contracts.payments.PaymentResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * HTTP client for payments-core-svc (port 8082).
 *
 * CLEAN PATTERN: Input and output types are contract types from banking-contracts.
 * No BFF-owned payment DTOs — all shapes defined once in the contracts library.
 */
@Component
class PaymentServiceClient(
    @Value("\${payments-service.base-url}") baseUrl: String
) {
    private val log = LoggerFactory.getLogger(PaymentServiceClient::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .build()

    fun submitPayment(request: PaymentRequest): PaymentResponse {
        return webClient.post()
            .uri("/api/v1/payments")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResponse::class.java)
            .block()!!
    }

    fun getPaymentsByAccount(accountId: String): List<PaymentResponse> {
        return try {
            webClient.get()
                .uri("/api/v1/payments/account/{accountId}", accountId)
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<List<PaymentResponse>>() {})
                .block() ?: emptyList()
        } catch (ex: Exception) {
            log.error("Failed to get payments for account {}: {}", accountId, ex.message)
            emptyList()
        }
    }
}
