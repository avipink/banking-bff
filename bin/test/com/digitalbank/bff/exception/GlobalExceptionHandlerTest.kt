package com.digitalbank.bff.exception

import com.digitalbank.bff.clean.client.AccountServiceClient
import com.digitalbank.bff.clean.client.PaymentServiceClient
import com.digitalbank.bff.clean.client.UpstreamPaymentException
import com.digitalbank.bff.clean.controller.DashboardController
import com.digitalbank.contracts.accounts.AccountStatus
import com.digitalbank.contracts.accounts.AccountSummary
import com.digitalbank.contracts.accounts.AccountType
import com.digitalbank.contracts.common.ApiError
import com.digitalbank.contracts.common.MonetaryAmount
import com.digitalbank.contracts.common.PaginatedResponse
import com.digitalbank.contracts.payments.PaymentRequest
import com.digitalbank.contracts.payments.PaymentType
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant

/**
 * Verifies that GlobalExceptionHandler in banking-bff correctly:
 * 1. Propagates UpstreamPaymentException (4xx) with original status + ApiError code.
 * 2. Collapses WebClientResponseException (5xx) to UPSTREAM_ERROR.
 * 3. Returns MISSING_HEADER for MissingRequestHeaderException.
 * 4. Returns structured ApiError for ResponseStatusException.
 */
@WebMvcTest(DashboardController::class)
class GlobalExceptionHandlerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var accountClient: AccountServiceClient

    @MockBean
    private lateinit var paymentClient: PaymentServiceClient

    private val transferRequest = PaymentRequest(
        fromAccountId = "ACC-001",
        toAccountId = "ACC-002",
        amount = MonetaryAmount("100.00", "USD"),
        type = PaymentType.INTERNAL_TRANSFER,
        reference = null
    )

    private val transferBody: String by lazy { objectMapper.writeValueAsString(transferRequest) }

    // -------------------------------------------------------------------------
    // UpstreamPaymentException — 4xx typed error propagation
    // -------------------------------------------------------------------------

    @Test
    fun `UpstreamPaymentException with 422 - propagates status and code unchanged`() {
        val apiError = ApiError(
            code = "INSUFFICIENT_FUNDS",
            message = "Insufficient funds",
            traceId = "trace-1",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(transferRequest, "key-1"))
            .willThrow(UpstreamPaymentException(HttpStatus.UNPROCESSABLE_ENTITY, apiError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
            .andExpect(jsonPath("$.traceId").value("trace-1"))
    }

    @Test
    fun `UpstreamPaymentException with 404 - propagates PAYMENT_ACCOUNT_NOT_FOUND`() {
        val apiError = ApiError(
            code = "PAYMENT_ACCOUNT_NOT_FOUND",
            message = "Account not found",
            traceId = "trace-2",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(transferRequest, "key-2"))
            .willThrow(UpstreamPaymentException(HttpStatus.NOT_FOUND, apiError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("PAYMENT_ACCOUNT_NOT_FOUND"))
    }

    @Test
    fun `UpstreamPaymentException - original ApiError body passed through without modification`() {
        val upstreamApiError = ApiError(
            code = "IDEMPOTENCY_KEY_REUSED",
            message = "Key 'key-3' was used with a different payload",
            traceId = "upstream-trace-99",
            timestamp = "2026-04-15T10:00:00Z"
        )
        given(paymentClient.submitPayment(transferRequest, "key-3"))
            .willThrow(UpstreamPaymentException(HttpStatus.CONFLICT, upstreamApiError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
            // Original traceId from upstream preserved — not overwritten by BFF
            .andExpect(jsonPath("$.traceId").value("upstream-trace-99"))
    }

    // -------------------------------------------------------------------------
    // WebClientResponseException — 5xx collapses to UPSTREAM_ERROR
    // -------------------------------------------------------------------------

    @Test
    fun `WebClientResponseException 500 - collapses to UPSTREAM_ERROR`() {
        given(paymentClient.submitPayment(transferRequest, "key-5xx"))
            .willThrow(
                WebClientResponseException.create(
                    500, "Internal Server Error",
                    org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null
                )
            )

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-5xx")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().is5xxServerError)
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
    }

    @Test
    fun `WebClientResponseException 503 - collapses to UPSTREAM_ERROR`() {
        given(paymentClient.submitPayment(transferRequest, "key-503"))
            .willThrow(
                WebClientResponseException.create(
                    503, "Service Unavailable",
                    org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null
                )
            )

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-503")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().is5xxServerError)
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
    }

    @Test
    fun `WebClientResponseException UPSTREAM_ERROR - response includes traceId and timestamp`() {
        given(paymentClient.submitPayment(transferRequest, "key-trace"))
            .willThrow(
                WebClientResponseException.create(
                    502, "Bad Gateway",
                    org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null
                )
            )

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().is5xxServerError)
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.message").value(containsString("502")))
    }

    // -------------------------------------------------------------------------
    // MissingRequestHeaderException
    // -------------------------------------------------------------------------

    @Test
    fun `missing Idempotency-Key header - returns 400 MISSING_HEADER`() {
        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferBody)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
            .andExpect(jsonPath("$.message").value(containsString("Idempotency-Key")))
    }

    // -------------------------------------------------------------------------
    // ResponseStatusException (404 from controller)
    // -------------------------------------------------------------------------

    @Test
    fun `ResponseStatusException 404 - returns structured ApiError`() {
        // Trigger via getDashboard indirectly: account client works, then dashboard hit
        // Use getAccountDetail path which throws ResponseStatusException on null account
        given(accountClient.getAccount("ACC-MISSING")).willReturn(null)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/dashboard/accounts/ACC-MISSING"
            )
        )
            .andExpect(status().isNotFound)
            // ResponseStatusException handler returns ApiError with code=statusCode string
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }
}
