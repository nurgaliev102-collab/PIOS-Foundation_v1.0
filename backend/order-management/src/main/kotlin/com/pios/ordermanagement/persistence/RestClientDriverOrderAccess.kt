package com.pios.ordermanagement.persistence

import com.pios.ordermanagement.application.DriverOrderAccess
import com.pios.ordermanagement.application.DriverOrderAccessUnavailableException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Asks Dispatch, the authority on proposals, which orders are visible to
 * this driver. The original Bearer token is forwarded so Dispatch applies
 * its own participant check; no service-side bypass credential exists.
 */
@Component
class RestClientDriverOrderAccess(
    @Value("\${pios.dispatch.base-url:http://127.0.0.1:8084}") baseUrl: String,
    @Value("\${pios.dispatch.connect-timeout-ms:1500}") connectTimeoutMs: Long,
    @Value("\${pios.dispatch.read-timeout-ms:3000}") readTimeoutMs: Long
) : DriverOrderAccess {
    private val client: RestClient = run {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    override fun accessibleOrderIds(driverId: String, authorization: String): Set<String> =
        try {
            client.get()
                .uri { builder ->
                    builder.path("/v1/proposals")
                        .queryParam("driverId", driverId)
                        .build()
                }
                .header("Authorization", authorization)
                .retrieve()
                .body(Array<ProposalAccessResponse>::class.java)
                .orEmpty()
                .mapTo(linkedSetOf()) { it.orderId }
        } catch (ex: Exception) {
            throw DriverOrderAccessUnavailableException(ex)
        }

    private data class ProposalAccessResponse(val orderId: String)
}
