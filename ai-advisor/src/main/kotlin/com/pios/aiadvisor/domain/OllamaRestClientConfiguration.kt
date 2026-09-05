package com.pios.aiadvisor.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Provides the [RestClient] [OllamaProvider] uses to reach a locally-hosted
 * Ollama instance's own OpenAI-compatible chat-completions endpoint —
 * mirrors [DeepSeekRestClientConfiguration] exactly, same Spring `RestClient`
 * mechanism, no new HTTP client dependency.
 *
 * This bean is **always** created, regardless of which `AIProvider` is
 * actually selected (`pios.ai-advisor.provider`) — it makes no network call
 * at construction time, so its existence is harmless when `MockAIProvider`
 * is active (the default) and Ollama is not installed at all.
 * [OllamaProvider] itself, not this configuration, is the conditional piece
 * (`@ConditionalOnProperty`).
 *
 * [baseUrl] defaults to `http://localhost:11434` — Ollama's own default
 * listen address. [timeoutMillis] bounds both connect and read time, same
 * reasoning as [DeepSeekRestClientConfiguration]'s own KDoc; no automatic
 * retry is configured anywhere in this component.
 */
@Configuration
class OllamaRestClientConfiguration(
    @Value("\${pios.ai-advisor.ollama.base-url:http://localhost:11434}") private val baseUrl: String,
    @Value("\${pios.ai-advisor.ollama.timeout-ms:30000}") private val timeoutMillis: Long
) {

    @Bean
    fun ollamaRestClient(): RestClient {
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
