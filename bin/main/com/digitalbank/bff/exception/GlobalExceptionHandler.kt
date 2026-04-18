package com.digitalbank.bff.exception

import com.digitalbank.bff.clean.client.UpstreamPaymentException
import com.digitalbank.contracts.common.ApiError
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ApiError> =
        ResponseEntity.status(ex.statusCode).body(
            ApiError(
                code = ex.statusCode.toString(),
                message = ex.reason ?: ex.message,
                traceId = UUID.randomUUID().toString(),
                timestamp = Instant.now().toString()
            )
        )

    /**
     * Propagates typed 4xx ApiError bodies from upstream services unchanged.
     *
     * Preserves error codes such as INSUFFICIENT_FUNDS, IDEMPOTENCY_KEY_REUSED,
     * CURRENCY_MISMATCH, VALIDATION_FAILED, and PAYMENT_ACCOUNT_NOT_FOUND so
     * frontend callers receive actionable error information rather than the
     * generic UPSTREAM_ERROR code.
     */
    @ExceptionHandler(UpstreamPaymentException::class)
    fun handleUpstreamPaymentError(ex: UpstreamPaymentException): ResponseEntity<ApiError> =
        ResponseEntity.status(ex.statusCode).body(ex.apiError)

    /**
     * Collapses 5xx and connectivity errors from upstream services to UPSTREAM_ERROR.
     * Internal service details are intentionally hidden from frontend callers.
     *
     * Note: 4xx responses from payments-core-svc are handled by [handleUpstreamPaymentError]
     * via [UpstreamPaymentException] before they reach this handler.
     */
    @ExceptionHandler(WebClientResponseException::class)
    fun handleUpstreamError(ex: WebClientResponseException): ResponseEntity<ApiError> =
        ResponseEntity.status(ex.statusCode).body(
            ApiError(
                code = "UPSTREAM_ERROR",
                message = "Upstream service returned ${ex.statusCode}: ${ex.statusText}",
                traceId = UUID.randomUUID().toString(),
                timestamp = Instant.now().toString()
            )
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ApiError> =
        ResponseEntity.status(400).body(
            ApiError(
                code = "MISSING_HEADER",
                message = "Required header '${ex.headerName}' is missing",
                traceId = UUID.randomUUID().toString(),
                timestamp = Instant.now().toString()
            )
        )
}
