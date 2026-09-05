package com.pios.aiadvisor.domain

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Provides the [RestClient] [QwenProvider] uses to reach Alibaba Cloud
 * DashScope's own OpenAI-compatible chat-completions endpoint — mirrors
 * [DeepSeekRestClientConfiguration]/[OllamaRestClientConfiguration] exactly,
 * same Spring `RestClient` mechanism, no new HTTP client dependency.
 *
 * This bean is **always** created, regardless of which `AIProvider` is
 * actually selected (`pios.ai-advisor.provider`) — it makes no network call
 * at construction time, so its existence is harmless when `MockAIProvider`
 * is active (the default) and no Qwen/DashScope account is configured at
 * all. [QwenProvider] itself, not this configuration, is the conditional
 * piece (`@ConditionalOnProperty`).
 *
 * [baseUrl] defaults to `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`
 * — DashScope's **international** OpenAI-compatibility base URL, not the
 * mainland-China one (`dashscope.aliyuncs.com`, no `-intl`), and not a
 * workspace-specific endpoint either. This is deliberate, not a typo: the
 * configured account's own API key lives in DashScope's Singapore region,
 * and a real API test from this deployment against a workspace-specific
 * endpoint returned `403 Workspace endpoint access denied` — the
 * `-intl` base URL is the one actually confirmed working for this
 * deployment's key (2026-08-17). Already includes `/compatible-mode/v1`;
 * [QwenProvider] appends only `/chat/completions` to it, never repeating
 * `/v1`. [timeoutMillis] bounds both connect and read time, same reasoning
 * as the other two `RestClientConfiguration`s' own KDoc; no automatic retry
 * is configured anywhere in this component.
 */
@Configuration
class QwenRestClientConfiguration(
    @Value("\${pios.ai-advisor.qwen.base-url:https://dashscope-intl.aliyuncs.com/compatible-mode/v1}") private val baseUrl: String,
    @Value("\${pios.ai-advisor.qwen.timeout-ms:30000}") private val timeoutMillis: Long
) {

    @Bean
    fun qwenRestClient(): RestClient {
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
