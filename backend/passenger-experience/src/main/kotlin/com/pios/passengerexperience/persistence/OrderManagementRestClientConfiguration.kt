package com.pios.passengerexperience.persistence

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Provides the [RestClient] [RestClientOrderSubmissionClient] uses to
 * reach Order Management's Submit Order REST endpoint (ADR-004, ADR-028;
 * Tranche 2: Passenger Experience REST Transport). Uses Spring's own
 * `RestClient` (already transitively available via
 * `spring-boot-starter-web` -- no new dependency), consistent with this
 * codebase's established convention of using Spring's simplest built-in
 * mechanism rather than a third-party HTTP client library.
 *
 * [CONNECT_TIMEOUT]/[READ_TIMEOUT] are bounded at the same order of
 * magnitude as [com.pios.ordermanagement.persistence.RabbitMQEventPublisher]'s
 * own publisher-confirm timeout (5000ms) -- an unbounded timeout could
 * make a caller of [RestClientOrderSubmissionClient] hang indefinitely.
 * No automatic retry is configured here (Part 9's central risk: Submit
 * Order is not idempotent, so a blind retry could create a duplicate
 * order).
 */
@Configuration
class OrderManagementRestClientConfiguration(
    @Value("\${pios.order-management.base-url}") private val orderManagementBaseUrl: String
) {

    @Bean
    fun orderManagementRestClient(): RestClient {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT)
        )
        return RestClient.builder()
            .baseUrl(orderManagementBaseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofMillis(5000)
        private val READ_TIMEOUT: Duration = Duration.ofMillis(5000)
    }
}
