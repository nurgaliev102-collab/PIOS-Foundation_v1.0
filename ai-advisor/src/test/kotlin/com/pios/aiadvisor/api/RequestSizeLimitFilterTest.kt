package com.pios.aiadvisor.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Proves the pure size-check ADR-056 Decision 8's oversized-request guard is built on (`RequestSizeLimitFilter` itself is a thin servlet wrapper around this). */
class RequestSizeLimitFilterTest {

    @Test
    fun `a body within the limit is not oversized`() {
        assertFalse(exceedsLimit(contentLength = 500, maxBytes = 8192))
    }

    @Test
    fun `a body exactly at the limit is not oversized`() {
        assertFalse(exceedsLimit(contentLength = 8192, maxBytes = 8192))
    }

    @Test
    fun `a body one byte over the limit is oversized`() {
        assertTrue(exceedsLimit(contentLength = 8193, maxBytes = 8192))
    }

    @Test
    fun `an unknown content length (-1, chunked transfer) is never treated as oversized`() {
        assertFalse(exceedsLimit(contentLength = -1, maxBytes = 8192))
    }
}
