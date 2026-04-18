package com.digitalbank.bff.clean.client

import com.digitalbank.contracts.common.ApiError
import org.springframework.http.HttpStatusCode

/**
 * Carries a typed 4xx error response from payments-core-svc through the BFF
 * call stack so [com.digitalbank.bff.exception.GlobalExceptionHandler] can
 * re-emit the upstream [ApiError] body and HTTP status unchanged.
 *
 * Only thrown for 4xx responses. 5xx responses are left as
 * [org.springframework.web.reactive.function.client.WebClientResponseException]
 * so the existing UPSTREAM_ERROR handler collapses them (hides internal detail).
 *
 * @property statusCode The HTTP status code returned by payments-core-svc
 * @property apiError The parsed [ApiError] body from the upstream response
 */
class UpstreamPaymentException(
    val statusCode: HttpStatusCode,
    val apiError: ApiError
) : RuntimeException(apiError.message)
