package com.pios.passengerexperience.persistence

import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Proves [RestClientOrderSubmissionClient] actually reaches a real,
 * separately-started Order Management instance
 * ([OrderManagementTestServer]), and that a connection failure, a
 * timeout, and a validation failure each surface as distinct exceptions
 * -- never silently converted into a successful result (Tranche 2:
 * Passenger Experience REST Transport, Part 7/Part 9).
 *
 * Order Provenance / Authentication Remediation (P0,
 * `docs/PIOS_DATA_FLOW_CODE_AUDIT.md` Section 5): [RestClientOrderSubmissionClient]
 * itself sends no `Authorization` header and has no notion of a session
 * token to send one with -- this REST path was already superseded as the
 * live product's own order-submission caller before this remediation
 * (`frontend/src/pages/RideRequest/RideRequest.tsx` calls Order
 * Management directly). The two tests below are updated, not deleted, to
 * assert the new, correct behavior: an unauthenticated caller is rejected
 * with 401, the same rule the live frontend caller must now satisfy too.
 */
class RestClientOrderSubmissionClientTest {

    @Test
    fun `submitting with no Authorization header is rejected with 401, since this client sends none`() {
        val client = RestClientOrderSubmissionClient(restClientFor(OrderManagementTestServer.baseUrl))

        assertFailsWith<HttpClientErrorException.Unauthorized> {
            client.submit("passenger-rest-1")
        }
    }

    @Test
    fun `submitting a blank passenger reference is still rejected with 401 -- auth is checked before content validation`() {
        val client = RestClientOrderSubmissionClient(restClientFor(OrderManagementTestServer.baseUrl))

        assertFailsWith<HttpClientErrorException.Unauthorized> {
            client.submit("")
        }
    }

    @Test
    fun `a connection failure surfaces as a distinct transport exception`() {
        val closedPort = reserveAndCloseAPort()
        val client = RestClientOrderSubmissionClient(restClientFor("http://127.0.0.1:$closedPort"))

        assertFailsWith<ResourceAccessException> {
            client.submit("passenger-rest-2")
        }
    }

    @Test
    fun `a read timeout surfaces as a distinct transport exception`() {
        ServerSocket(0).use { serverSocket ->
            // A listening socket that never accepts/responds: the TCP
            // handshake completes (so this is genuinely a timeout, not a
            // connection refusal), but no HTTP response ever arrives.
            val client = RestClientOrderSubmissionClient(
                restClientFor("http://127.0.0.1:${serverSocket.localPort}", readTimeout = Duration.ofMillis(300))
            )

            // Asserted as the broader RestClientException, not the more
            // specific ResourceAccessException: depending on exactly when
            // the underlying SimpleClientHttpRequestFactory's
            // HttpURLConnection reads the timed-out socket (during the
            // initial request or while RestClient's own status handler
            // inspects the response), Spring surfaces either type -- both
            // wrap the same root SocketTimeoutException, and either is
            // clearly distinct from the 400 thrown for a validation
            // failure, which is what this test and Part 9 both require.
            val exception = assertFailsWith<RestClientException> {
                client.submit("passenger-rest-3")
            }
            assertTrue(generateSequence<Throwable>(exception) { it.cause }.any { it is SocketTimeoutException })
        }
    }

    private fun reserveAndCloseAPort(): Int = ServerSocket(0).use { it.localPort }

    private fun restClientFor(
        baseUrl: String,
        connectTimeout: Duration = Duration.ofMillis(2000),
        readTimeout: Duration = Duration.ofMillis(5000)
    ): RestClient {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout)
        )
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
