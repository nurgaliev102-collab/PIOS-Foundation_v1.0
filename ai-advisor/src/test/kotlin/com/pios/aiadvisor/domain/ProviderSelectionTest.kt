package com.pios.aiadvisor.domain

import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves ADR-056 Rollout Step 3's own explicit requirement, extended to PIOS
 * Intelligence's local and real-hosted steps: `MockAIProvider` is the
 * [AIProvider] bean Spring wires up when `pios.ai-advisor.provider` is
 * unset — the existing pilot must never depend on a funded DeepSeek/Qwen
 * account, or on Ollama being installed, existing. Uses Spring Boot's own
 * `ApplicationContextRunner` (already part of `spring-boot-starter-test`, no
 * new dependency) to test `@ConditionalOnProperty` wiring without a full
 * application context.
 *
 * Uses the **real** [DeepSeekRestClientConfiguration],
 * [OllamaRestClientConfiguration], and [QwenRestClientConfiguration] — not a
 * fake stand-in — specifically so this test proves the real production
 * shape: three genuine `RestClient` beans (`deepSeekRestClient`,
 * `ollamaRestClient`, `qwenRestClient`) present in the same context at once,
 * each correctly disambiguated by Spring's constructor-parameter-name
 * matching against [DeepSeekProvider]'s, [OllamaProvider]'s, and
 * [QwenProvider]'s own constructor parameter names. None of the three
 * configurations makes a network call at construction time (each binds only
 * a base URL and a timeout), so this stays exactly as safe to run without
 * Ollama/DeepSeek/Qwen installed or configured as the rest of this module's
 * own tests.
 */
class ProviderSelectionTest {

    private fun contextRunner() = ApplicationContextRunner()
        .withUserConfiguration(
            DeepSeekRestClientConfiguration::class.java,
            OllamaRestClientConfiguration::class.java,
            QwenRestClientConfiguration::class.java,
            MockAIProvider::class.java,
            DeepSeekProvider::class.java,
            OllamaProvider::class.java,
            QwenProvider::class.java
        )

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
    fun `with provider=deepseek, DeepSeekProvider is selected instead of MockAIProvider or OllamaProvider`() {
        contextRunner().withPropertyValues("pios.ai-advisor.provider=deepseek").run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is DeepSeekProvider)
            assertTrue(context.getBeansOfType(AIProvider::class.java).size == 1)
        }
    }

    @Test
    fun `with provider=ollama, OllamaProvider is selected instead of MockAIProvider or DeepSeekProvider`() {
        contextRunner().withPropertyValues("pios.ai-advisor.provider=ollama").run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is OllamaProvider)
            assertTrue(context.getBeansOfType(AIProvider::class.java).size == 1)
        }
    }

    @Test
    fun `with provider=qwen, QwenProvider is selected instead of MockAIProvider, DeepSeekProvider or OllamaProvider`() {
        contextRunner().withPropertyValues("pios.ai-advisor.provider=qwen").run { context ->
            assertTrue(context.getBean(AIProvider::class.java) is QwenProvider)
            assertTrue(context.getBeansOfType(AIProvider::class.java).size == 1)
        }
    }
}
