package com.pios.identity.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.pios.identity.application.OutboundSmsPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Duration

/** Production has exactly one SMS port and fails startup when delivery is unconfigured. */
@Configuration
class SmsRuConfiguration(
    @Value("\${PIOS_SMS_API_ID:}") private val apiId: String,
    @Value("\${PIOS_SMS_SENDER:}") private val sender: String,
    @Value("\${PIOS_SMS_API_BASE_URL:https://sms.ru}") private val baseUrl: String,
    @Value("\${PIOS_SMS_CONNECT_TIMEOUT_MS:2000}") private val connectTimeoutMs: Long,
    @Value("\${PIOS_SMS_READ_TIMEOUT_MS:5000}") private val readTimeoutMs: Long
) {
    @Bean
    fun outboundSmsPort(objectMapper: ObjectMapper): OutboundSmsPort {
        require(apiId.isNotBlank()) { "PIOS_SMS_API_ID is required" }
        require(sender.isNotBlank()) { "PIOS_SMS_SENDER is required" }
        require(connectTimeoutMs in 1..30_000) { "PIOS_SMS_CONNECT_TIMEOUT_MS must be within 1..30000" }
        require(readTimeoutMs in 1..30_000) { "PIOS_SMS_READ_TIMEOUT_MS must be within 1..30000" }
        val endpoint = try {
            URI.create(baseUrl)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("PIOS_SMS_API_BASE_URL must be an HTTPS origin")
        }
        require(endpoint.scheme == "https" && endpoint.host != null && endpoint.rawUserInfo == null &&
            endpoint.rawPath.isNullOrBlank() && endpoint.rawQuery == null && endpoint.rawFragment == null
        ) { "PIOS_SMS_API_BASE_URL must be an HTTPS origin" }
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs))
        )
        val client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build()
        return SmsRuOutboundSmsAdapter(client, objectMapper, apiId, sender)
    }
}
