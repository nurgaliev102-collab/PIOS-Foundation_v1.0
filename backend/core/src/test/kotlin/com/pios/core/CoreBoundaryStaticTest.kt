package com.pios.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Structural guard tests — they scan Core's own `src/main` source tree
 * (with comments stripped, so KDoc that *documents* an exclusion does not
 * trip them) and fail if a forbidden pattern appears in actual code. They
 * enforce ADR-067's hard boundaries at build time, not just by review:
 *
 *  - Core is **consumer-only**: no event publisher, no outbox, no
 *    `RabbitTemplate.convertAndSend`.
 *  - Core reads/writes **only `pios_core`**: no other module's database
 *    name, no `com.pios.<other-module>` import, no Taxi REST client.
 *  - Core listens to **only the five Current Authorized Inputs**: no
 *    `DriverAvailabilityChanged` and no Reserved Future Input appears as a
 *    string literal / identifier in code.
 *
 * No Spring context, no database, no broker.
 */
class CoreBoundaryStaticTest {

    private val strippedMainSources: List<Pair<String, String>> by lazy {
        val root = File("src/main/kotlin/com/pios/core")
        assertTrue(root.isDirectory, "expected Core main sources at ${root.absolutePath}")
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name to stripComments(it.readText()) }
            .toList()
    }

    /** Removes `/* ... */` (KDoc included) and `//` line comments. */
    private fun stripComments(source: String): String {
        val noBlock = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        return noBlock.lineSequence().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }

    private fun eachSource(assertion: (fileName: String, code: String) -> Unit) {
        strippedMainSources.forEach { (name, code) -> assertion(name, code) }
    }

    @Test
    fun `Core publishes no event - no producer type anywhere in code`() {
        eachSource { name, code ->
            listOf("convertAndSend", "RabbitTemplate", "EventPublisher", "OutboxRepository", "OutboxRecord", "OutboxRelay")
                .forEach { forbidden ->
                    assertTrue(!code.contains(forbidden), "$name must not contain '$forbidden' (Core is consumer-only, ADR-067 Event Boundary)")
                }
        }
    }

    @Test
    fun `Core connects to no database but pios_core`() {
        eachSource { name, code ->
            listOf("pios_dispatch", "pios_order_management", "pios_identity", "pios_driver_management", "pios_passenger_experience", "pios_network_management")
                .forEach { forbidden ->
                    assertTrue(!code.contains(forbidden), "$name must not reference database '$forbidden' (ADR-067 Write Boundary)")
                }
        }
    }

    @Test
    fun `Core imports no other module's types and no HTTP client to a Taxi module`() {
        eachSource { name, code ->
            listOf(
                "import com.pios.dispatch", "import com.pios.ordermanagement", "import com.pios.identity",
                "import com.pios.drivermanagement", "import com.pios.passengerexperience", "import com.pios.networkmanagement",
                "RestTemplate", "WebClient", "RestClient"
            ).forEach { forbidden ->
                assertTrue(!code.contains(forbidden), "$name must not contain '$forbidden' (ADR-067 Write Boundary — no cross-module read/call)")
            }
        }
    }

    @Test
    fun `no listener or routing key for DriverAvailabilityChanged or any Reserved Future Input, in code`() {
        val forbiddenEventTokens = listOf(
            "DriverAvailabilityChanged", "driver.availability.changed",
            "PrimaryConnectionCleared", "primary.connection.cleared",
            "OrderAssigned", "order.assigned",
            "OrderProposed", "ProposalAccepted", "ProposalDeclined", "ProposalLapsed",
            "AssignmentArrived", "assignment.arrived",
            "AssignmentStarted", "assignment.started",
            "AssignmentAccepted", "assignment.accepted",
            "TripArrived", "trip.arrived", "TripStarted", "trip.started", "TripCompleted", "trip.completed"
        )
        eachSource { name, code ->
            forbiddenEventTokens.forEach { token ->
                assertTrue(!code.contains(token), "$name (comments stripped) must not mention '$token' — DriverAvailabilityChanged or a Reserved Future Input (ADR-067)")
            }
        }
    }

    @Test
    fun `exactly the five authorized routing keys are bound`() {
        val allCode = strippedMainSources.joinToString("\n") { it.second }
        listOf("order.submitted", "order.completed", "order.cancelled", "assignment.completed", "primary.connection.designated")
            .forEach { key ->
                assertTrue(allCode.contains("\"$key\""), "expected authorized routing key '$key' as a string literal somewhere in Core")
            }
    }
}
