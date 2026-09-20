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
import java.nio.charset.StandardCharsets
import java.time.Duration

/** One production SMS port; missing credentials fail startup without exposing values. */
@Configuration
class SmsAeroConfiguration(
    @Value("\${PIOS_SMS_LOGIN:}") private val login: String,
    @Value("\${PIOS_SMS_API_KEY:}") private val apiKey: String,
    @Value("\${PIOS_SMS_SENDER:}") private val sender: String,
    @Value("\${PIOS_SMS_API_BASE_URL:https://gate.smsaero.ru}") private val baseUrl: String,
    @Value("\${PIOS_SMS_CONNECT_TIMEOUT_MS:2000}") private val connectTimeoutMs: Long,
    @Value("\${PIOS_SMS_READ_TIMEOUT_MS:5000}") private val readTimeoutMs: Long
) {
    @Bean
    fun outboundSmsPort(objectMapper: ObjectMapper): OutboundSmsPort {
        require(login.isNotBlank()) { "PIOS_SMS_LOGIN is required" }
        require(apiKey.isNotBlank()) { "PIOS_SMS_API_KEY is required" }
        require(sender.isNotBlank()) { "PIOS_SMS_SENDER is required" }
        require(connectTimeoutMs in 1..30_000) { "PIOS_SMS_CONNECT_TIMEOUT_MS must be within 1..30000" }
        require(readTimeoutMs in 1..30_000) { "PIOS_SMS_READ_TIMEOUT_MS must be within 1..30000" }
        val endpoint = try {
            URI.create(baseUrl)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("PIOS_SMS_API_BASE_URL must be the SMS Aero HTTPS origin")
        }
        // Prevent a stale SMS.RU URL (or another host) from receiving the new
        // Basic Auth credential after migration. Tests use a direct adapter
        // with a loopback fake server and never change production wiring.
        require(endpoint.scheme == "https" && endpoint.host == "gate.smsaero.ru" &&
            endpoint.rawUserInfo == null && endpoint.rawPath.isNullOrBlank() &&
            endpoint.rawQuery == null && endpoint.rawFragment == null
        ) { "PIOS_SMS_API_BASE_URL must be the SMS Aero HTTPS origin" }
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs))
        )
        val client = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders { headers -> headers.setBasicAuth(login, apiKey, StandardCharsets.UTF_8) }
            .requestFactory(requestFactory)
            .build()
        return SmsAeroOutboundSmsAdapter(client, objectMapper, sender)
    }
}
