package com.pios.aiadvisor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * AI Advisor for Owner Control Center (ADR-056, Accepted 2026-08-17). Not a
 * PIOS module (ADR-056 Decision 2, mirroring ADR-046 Decision 1): owns no
 * capability, models no domain, holds no data. Rollout Step 2: exposes
 * `POST /v1/advisor/analyze` backed by [com.pios.aiadvisor.domain.MockAIProvider]
 * only — no real provider (DeepSeek/Claude), no API key, at this stage.
 */
@SpringBootApplication
class AiAdvisorApplication

fun main(args: Array<String>) {
    runApplication<AiAdvisorApplication>(*args)
}
