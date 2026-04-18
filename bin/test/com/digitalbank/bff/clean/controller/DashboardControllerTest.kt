package com.digitalbank.bff.clean.controller

import com.digitalbank.bff.clean.client.AccountServiceClient
import com.digitalbank.bff.clean.client.PaymentServiceClient
import com.digitalbank.bff.clean.client.UpstreamPaymentException
import com.digitalbank.contracts.accounts.AccountStatus
import com.digitalbank.contracts.accounts.AccountSummary
import com.digitalbank.contracts.accounts.AccountType
import com.digitalbank.contracts.common.ApiError
import com.digitalbank.contracts.common.MonetaryAmount
import com.digitalbank.contracts.common.PaginatedResponse
import com.digitalbank.contracts.payments.PaymentRequest
import com.digitalbank.contracts.payments.PaymentResponse
import com.digitalbank.contracts.payments.PaymentStatus
import com.digitalbank.contracts.payments.PaymentType
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(DashboardController::class)
class DashboardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var accountClient: AccountServiceClient

    @MockBean
    private lateinit var paymentClient: PaymentServiceClient

    private val sampleAccount = AccountSummary(
        accountId = "ACC-001",
        accountType = AccountType.CHECKING,
        holderName = "Alice",
        balance = MonetaryAmount("5000.00", "USD"),
        status = AccountStatus.ACTIVE,
        openedAt = "2025-01-01T00:00:00Z"
    )

    private val samplePayment = PaymentResponse(
        paymentId = "PAY-004",
        fromAccountId = "ACC-001",
        toAccountId = "ACC-002",
        amount = MonetaryAmount("100.00", "USD"),
        type = PaymentType.INTERNAL_TRANSFER,
        status = PaymentStatus.PENDING,
        reference = "test",
        createdAt = "2026-04-15T10:00:00Z"
    )

    private val validTransferRequest = PaymentRequest(
        fromAccountId = "ACC-001",
        toAccountId = "ACC-002",
        amount = MonetaryAmount("100.00", "USD"),
        type = PaymentType.INTERNAL_TRANSFER,
        reference = "test"
    )

    // -------------------------------------------------------------------------
    // POST /api/v1/dashboard/transfer
    // -------------------------------------------------------------------------

    @Test
    fun `POST transfer - valid request with Idempotency-Key - returns 201`() {
        given(paymentClient.submitPayment(validTransferRequest, "key-001"))
            .willReturn(samplePayment)

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.paymentId").value("PAY-004"))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `POST transfer - missing Idempotency-Key - returns 400 MISSING_HEADER`() {
        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
    }

    @Test
    fun `POST transfer - upstream 4xx propagated via UpstreamPaymentException`() {
        val upstreamError = ApiError(
            code = "INSUFFICIENT_FUNDS",
            message = "Insufficient funds in account: ACC-001",
            traceId = "trace-123",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(validTransferRequest, "key-funds"))
            .willThrow(UpstreamPaymentException(HttpStatus.UNPROCESSABLE_ENTITY, upstreamError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-funds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
    }

    @Test
    fun `POST transfer - upstream IDEMPOTENCY_KEY_REUSED - returns 409`() {
        val upstreamError = ApiError(
            code = "IDEMPOTENCY_KEY_REUSED",
            message = "Idempotency key reused",
            traceId = "trace-456",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(validTransferRequest, "key-conflict"))
            .willThrow(UpstreamPaymentException(HttpStatus.CONFLICT, upstreamError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
    }

    @Test
    fun `POST transfer - upstream VALIDATION_FAILED - returns 400`() {
        val upstreamError = ApiError(
            code = "VALIDATION_FAILED",
            message = "fromAccountId: must not be blank",
            traceId = "trace-789",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(validTransferRequest, "key-val"))
            .willThrow(UpstreamPaymentException(HttpStatus.BAD_REQUEST, upstreamError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-val")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }

    @Test
    fun `POST transfer - upstream CURRENCY_MISMATCH - returns 422`() {
        val upstreamError = ApiError(
            code = "CURRENCY_MISMATCH",
            message = "Request currency USD does not match EUR",
            traceId = "trace-curr",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(validTransferRequest, "key-curr"))
            .willThrow(UpstreamPaymentException(HttpStatus.UNPROCESSABLE_ENTITY, upstreamError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-curr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"))
    }

    @Test
    fun `POST transfer - upstream DAILY_LIMIT_EXCEEDED - returns 422`() {
        val upstreamError = ApiError(
            code = "DAILY_LIMIT_EXCEEDED",
            message = "Daily outbound limit exceeded",
            traceId = "trace-limit",
            timestamp = Instant.now().toString()
        )
        given(paymentClient.submitPayment(validTransferRequest, "key-limit"))
            .willThrow(UpstreamPaymentException(HttpStatus.UNPROCESSABLE_ENTITY, upstreamError))

        mockMvc.perform(
            post("/api/v1/dashboard/transfer")
                .header("Idempotency-Key", "key-limit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validTransferRequest))
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"))
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/dashboard
    // -------------------------------------------------------------------------

    @Test
    fun `GET dashboard - returns 200 with accounts and recent payments`() {
        val paginatedAccounts = PaginatedResponse(
            items = listOf(sampleAccount),
            page = 1,
            pageSize = 20,
            totalItems = 1L,
            totalPages = 1
        )
        given(accountClient.listAccounts()).willReturn(paginatedAccounts)
        given(paymentClient.getPaymentsByAccount("ACC-001")).willReturn(listOf(samplePayment))

        mockMvc.perform(get("/api/v1/dashboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAccountCount").value(1))
            .andExpect(jsonPath("$.accounts[0].accountId").value("ACC-001"))
            .andExpect(jsonPath("$.recentPayments[0].paymentId").value("PAY-004"))
    }

    @Test
    fun `GET dashboard - no accounts - returns 200 with empty lists`() {
        val empty = PaginatedResponse<AccountSummary>(
            items = emptyList(),
            page = 1,
            pageSize = 20,
            totalItems = 0L,
            totalPages = 0
        )
        given(accountClient.listAccounts()).willReturn(empty)

        mockMvc.perform(get("/api/v1/dashboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAccountCount").value(0))
            .andExpect(jsonPath("$.accounts").isEmpty)
            .andExpect(jsonPath("$.recentPayments").isEmpty)
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/dashboard/accounts/{id}
    // -------------------------------------------------------------------------

    @Test
    fun `GET account detail - account found - returns 200 with payments`() {
        val fullAccount = com.digitalbank.contracts.accounts.AccountResponse(
            accountId = "ACC-001",
            accountType = AccountType.CHECKING,
            holderName = "Alice",
            balance = MonetaryAmount("5000.00", "USD"),
            status = AccountStatus.ACTIVE,
            currency = "USD",
            lastTransactionDate = null,
            createdAt = "2025-01-01T00:00:00Z"
        )
        given(accountClient.getAccount("ACC-001")).willReturn(fullAccount)
        given(paymentClient.getPaymentsByAccount("ACC-001")).willReturn(listOf(samplePayment))

        mockMvc.perform(get("/api/v1/dashboard/accounts/ACC-001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.account.accountId").value("ACC-001"))
            .andExpect(jsonPath("$.recentPayments[0].paymentId").value("PAY-004"))
    }

    @Test
    fun `GET account detail - account not found - returns 404`() {
        given(accountClient.getAccount("ACC-999")).willReturn(null)

        mockMvc.perform(get("/api/v1/dashboard/accounts/ACC-999"))
            .andExpect(status().isNotFound)
    }
}
