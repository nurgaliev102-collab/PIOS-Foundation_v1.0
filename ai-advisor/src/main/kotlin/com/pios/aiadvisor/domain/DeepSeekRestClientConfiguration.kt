package com.pios.aiadvisor.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Provides the [RestClient] [DeepSeekProvider] uses to reach DeepSeek's own
 * OpenAI-compatible chat-completions API (ADR-056 Rollout Step 3). Uses
 * Spring's own `RestClient` — same mechanism, same "no new HTTP client
 * dependency" convention
 * `backend/passenger-experience/.../OrderManagementRestClientConfiguration.kt`
 * already established for cross-module calls, applied here to an external
 * provider instead of a PIOS module.
 *
 * This bean is **always** created, regardless of which `AIProvider` is
 * actually selected (`pios.ai-advisor.provider`) — it makes no network call
 * at construction time, so its existence is harmless when `MockAIProvider`
 * is active (the default) and no DeepSeek configuration has been set at
 * all. [DeepSeekProvider] itself, not this configuration, is the
 * conditional piece (`@ConditionalOnProperty`).
 *
 * [timeoutMillis] bounds both connect and read time — an unbounded call to
 * an external provider could otherwise hang the request thread
 * indefinitely, the same reasoning
 * `OrderManagementRestClientConfiguration`'s own KDoc already gives for its
 * sibling bean. ADR-056 Decision 8's own recommended starting value (30s)
 * is the default; no automatic retry is configured anywhere in this
 * component (ADR-056's own explicit prohibition).
 */
@Configuration
class DeepSeekRestClientConfiguration(
    @Value("\${pios.ai-advisor.deepseek.base-url:https://api.deepseek.com}") private val baseUrl: String,
    @Value("\${pios.ai-advisor.deepseek.timeout-ms:30000}") private val timeoutMillis: Long
) {

    @Bean
    fun deepSeekRestClient(): RestClient {
        val requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutMillis))
                .withReadTimeout(Duration.ofMillis(timeoutMillis))
        )
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
