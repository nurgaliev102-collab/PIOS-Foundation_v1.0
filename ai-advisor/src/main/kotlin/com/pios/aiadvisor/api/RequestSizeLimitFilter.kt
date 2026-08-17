package com.pios.aiadvisor.api

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * ADR-056 Decision 8: rejects an oversized request body from
 * `Content-Length` alone, before Spring ever attempts to parse it as JSON —
 * an oversized or malformed-by-size request costs nothing beyond reading
 * one header. `PilotAnalysisRequest` (ADR-056 Decision 5) is a small,
 * fixed-shape DTO of counts and rates; [maxBodyBytes]'s default (8 KB) is
 * generously larger than that DTO ever needs to be, tight enough that no
 * malicious or malformed oversized body reaches [com.pios.aiadvisor.domain.AIProvider].
 */
@Component
class RequestSizeLimitFilter(
    @Value("\${pios.ai-advisor.max-request-body-bytes:8192}") private val maxBodyBytes: Long
) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request is HttpServletRequest && response is HttpServletResponse && exceedsLimit(request.contentLengthLong, maxBodyBytes)) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.contentType = "application/json"
            response.writer.write("""{"outcome":"invalid_input","result":null,"message":"Request too large"}""")
            return
        }
        chain.doFilter(request, response)
    }
}

/** Pulled out as a pure function so the size decision is directly testable without a servlet container. `-1` (length unknown, chunked transfer) is never treated as oversized. */
fun exceedsLimit(contentLength: Long, maxBytes: Long): Boolean = contentLength in 0..Long.MAX_VALUE && contentLength > maxBytes
