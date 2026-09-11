package com.pios.core.qa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the fail-closed [CoreQaSafetyGate] — every FAIL branch
 * ADR-067 QA / Production Gate item 4 requires. No I/O, no connection.
 *
 * These run on any machine, in any environment, including this one where
 * a production PostgreSQL/RabbitMQ is present — the whole point is that
 * the Gate is a pure predicate.
 */
class CoreQaSafetyGateTest {

    private val validQaPgUrl = "jdbc:postgresql://127.0.0.1:5433/pios_core_test"
    private val validQaPgUser = "pios_core_qa"
    private val validQaPgPass = "a-qa-role-password"

    // ---------------- PostgreSQL: FAIL cases ----------------

    @Test
    fun `absent QA PostgreSQL configuration fails`() {
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validatePostgres(null, null, null) }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validatePostgres("", validQaPgUser, validQaPgPass) }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validatePostgres("   ", validQaPgUser, validQaPgPass) }
    }

    @Test
    fun `an accidental 127_0_0_1 colon 5432 endpoint fails - it is the production endpoint`() {
        val ex = assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validatePostgres("jdbc:postgresql://127.0.0.1:5432/pios_core_test", validQaPgUser, validQaPgPass)
        }
        assertTrue(ex.message!!.contains("production PostgreSQL endpoint"), ex.message)
    }

    @Test
    fun `localhost and IPv6 forms of the production endpoint fail`() {
        listOf(
            "jdbc:postgresql://localhost:5432/pios_core_test",
            "jdbc:postgresql://[::1]:5432/pios_core_test",
            "jdbc:postgresql://0.0.0.0:5432/pios_core_test"
        ).forEach {
            assertFailsWith<QaSafetyViolation>("$it must fail") { CoreQaSafetyGate.validatePostgres(it, validQaPgUser, validQaPgPass) }
        }
    }

    @Test
    fun `a production database name fails`() {
        listOf("pios_core", "pios_dispatch", "pios_order_management", "pios_identity", "pios_driver_management_qa", "postgres", "pios")
            .forEach { db ->
                val ex = assertFailsWith<QaSafetyViolation>("$db must fail") {
                    CoreQaSafetyGate.validatePostgres("jdbc:postgresql://127.0.0.1:5433/$db", validQaPgUser, validQaPgPass)
                }
                assertTrue(ex.message!!.contains("production"), ex.message)
            }
    }

    @Test
    fun `a non pios_core_test database name fails even if not a known production name`() {
        assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validatePostgres("jdbc:postgresql://127.0.0.1:5433/pios_core_test_v2", validQaPgUser, validQaPgPass)
        }
    }

    @Test
    fun `the postgres superuser fails`() {
        val ex = assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validatePostgres(validQaPgUrl, "postgres", validQaPgPass)
        }
        assertTrue(ex.message!!.contains("superuser"), ex.message)
    }

    @Test
    fun `an empty password fails - it is the trust-auth tell`() {
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validatePostgres(validQaPgUrl, validQaPgUser, "") }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validatePostgres(validQaPgUrl, validQaPgUser, null) }
    }

    @Test
    fun `an unparseable url or a url without an explicit port fails as ambiguous`() {
        listOf(
            "not-a-url",
            "jdbc:mysql://127.0.0.1:5433/pios_core_test",
            "jdbc:postgresql://127.0.0.1/pios_core_test",       // no port
            "jdbc:postgresql://127.0.0.1:5433",                  // no db
            "postgresql://127.0.0.1:5433/pios_core_test"         // missing jdbc: scheme
        ).forEach {
            assertFailsWith<QaSafetyViolation>("$it must fail") { CoreQaSafetyGate.validatePostgres(it, validQaPgUser, validQaPgPass) }
        }
    }

    @Test
    fun `the pilot VPS PostgreSQL endpoint fails`() {
        assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validatePostgres("jdbc:postgresql://62.217.176.214:5432/pios_core_test", validQaPgUser, validQaPgPass)
        }
        assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validatePostgres("jdbc:postgresql://62.217.176.214:5433/pios_core_test", validQaPgUser, validQaPgPass)
        }
    }

    // ---------------- PostgreSQL: PASS case ----------------

    @Test
    fun `a correct QA PostgreSQL endpoint passes`() {
        CoreQaSafetyGate.validatePostgres(validQaPgUrl, validQaPgUser, validQaPgPass)
        CoreQaSafetyGate.validatePostgres("jdbc:postgresql://127.0.0.1:5544/pios_core_test?ApplicationName=core-qa", "core_qa_role", "x")
    }

    // ---------------- RabbitMQ: FAIL cases ----------------

    @Test
    fun `the production vhost slash fails`() {
        val ex = assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, "/", "pios_test")
        }
        assertTrue(ex.message!!.contains("'/'"), ex.message)
    }

    @Test
    fun `a blank or url-encoded default vhost fails`() {
        listOf("", " ", "%2f", "%2F").forEach {
            assertFailsWith<QaSafetyViolation>("vhost '$it' must fail") {
                CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, it, "pios_test")
            }
        }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, null, "pios_test") }
    }

    @Test
    fun `the guest user fails`() {
        val ex = assertFailsWith<QaSafetyViolation> {
            CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, "pios-test", "guest")
        }
        assertTrue(ex.message!!.contains("guest"), ex.message)
    }

    @Test
    fun `the pilot VPS RabbitMQ host fails at any port`() {
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("62.217.176.214", 5672, "pios-test", "pios_test") }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("piosapp.ru", 5673, "pios-test", "pios_test") }
    }

    @Test
    fun `absent or invalid RabbitMQ host or port fails`() {
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq(null, 5672, "pios-test", "pios_test") }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("127.0.0.1", null, "pios-test", "pios_test") }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 0, "pios-test", "pios_test") }
        assertFailsWith<QaSafetyViolation> { CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, "pios-test", null) }
    }

    // ---------------- RabbitMQ: PASS case ----------------

    @Test
    fun `the ratified pios-test namespace passes`() {
        CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5672, "pios-test", "pios_test")
        CoreQaSafetyGate.validateRabbitMq("127.0.0.1", 5673, "pios-core-qa", "core_qa_user")
    }

    // ---------------- CoreQaEndpoints wiring ----------------

    @Test
    fun `CoreQaEndpoints_postgres fails closed when nothing is configured`() {
        // No -Dpios.core.qa.postgres.* is set for the unit-test run, and no
        // PIOS_CORE_QA_POSTGRES_* env var is expected — so this must throw.
        val hasConfig = System.getProperty("pios.core.qa.postgres.url") != null ||
            System.getenv("PIOS_CORE_QA_POSTGRES_URL") != null
        if (!hasConfig) {
            assertFailsWith<QaSafetyViolation> { CoreQaEndpoints.postgres() }
        }
    }

    @Test
    fun `CoreQaEndpoints_rabbitmq defaults to the broker-refused pios-test namespace and passes the gate`() {
        val hasOverride = listOf("host", "vhost", "username").any {
            System.getProperty("pios.core.qa.rabbitmq.$it") != null
        }
        if (!hasOverride) {
            val mq = CoreQaEndpoints.rabbitmq() // must not throw
            assertEquals("pios-test", mq.virtualHost)
            assertEquals("pios_test", mq.username)
            assertTrue(mq.virtualHost != "/" && mq.username != "guest")
        }
    }
}
