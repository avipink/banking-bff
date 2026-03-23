package com.digitalbank.bff.exception

import com.digitalbank.contracts.common.ApiError
import org.springframework.http.ResponseEntity
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
}
