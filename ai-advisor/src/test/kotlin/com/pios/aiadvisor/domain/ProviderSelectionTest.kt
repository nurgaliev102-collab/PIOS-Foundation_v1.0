package com.pios.aiadvisor.domain

import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves ADR-056 Rollout Step 3's own explicit requirement: `MockAIProvider`
 * is the [AIProvider] bean Spring wires up when `pios.ai-advisor.provider`
 * is unset — the existing pilot must never depend on a funded DeepSeek
 * account existing. Uses Spring Boot's own `ApplicationContextRunner`
 * (already part of `spring-boot-starter-test`, no new dependency) to test
 * `@ConditionalOnProperty` wiring without a full application context.
 */
class ProviderSelectionTest {

    private fun contextRunner() = ApplicationContextRunner()
        .withUserConfiguration(FakeRestClientConfiguration::class.java, MockAIProvider::class.java, DeepSeekProvider::class.java)

    @Configuration
    class FakeRestClientConfiguration {
        @Bean
        fun deepSeekRestClient(): RestClient = RestClient.builder().baseUrl("http://127.0.0.1:1").build()
    }

    @Test
    fun `with no provider property set, MockAIProvider is the only AIProvider bean`() {
        contextRunner().run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is MockAIProvider)
            assertTrue(context.getBeansOfType(AIProvider::class.java).size == 1)
        }
    }

    @Test
    fun `with provider=mock explicitly, MockAIProvider is selected`() {
        contextRunner().withPropertyValues("pios.ai-advisor.provider=mock").run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is MockAIProvider)
        }
    }

    @Test
    fun `with provider=deepseek, DeepSeekProvider is selected instead of MockAIProvider`() {
        contextRunner().withPropertyValues("pios.ai-advisor.provider=deepseek").run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is DeepSeekProvider)
            assertTrue(context.getBeansOfType(AIProvider::class.java).size == 1)
        }
    }
}
