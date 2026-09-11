package com.pios.core.qa

/**
 * Thrown by [CoreQaSafetyGate] when a Core QA endpoint configuration could
 * reach a production PostgreSQL endpoint or a production RabbitMQ
 * namespace, or is absent / ambiguous.
 *
 * ADR-067 QA / Production Gate item 4: *"QA execution MUST fail closed if
 * the test process can address a production PostgreSQL endpoint or a
 * production RabbitMQ namespace."* Absent QA configuration = FAIL. An
 * ambiguous or unparseable value = FAIL. A value that resolves to any
 * production endpoint / namespace / database name = FAIL.
 */
class QaSafetyViolation(message: String) : IllegalStateException(message)

/**
 * The automated Safety Gate. Pure validation — no I/O, no connection. It
 * runs (via [CoreQaEndpoints]) **before** any Core integration test opens
 * a PostgreSQL or RabbitMQ connection, and every FAIL branch here has a
 * dedicated unit test (`CoreQaSafetyGateTest`).
 *
 * QA isolation is defined not by the physical host but by the
 * impossibility of a Core test connection reaching production:
 *
 *  - **PostgreSQL** has no per-connection namespace primitive, so a QA
 *    PostgreSQL must be a *distinct instance* — a different `host:port`
 *    from the production endpoint — serving exactly `pios_core_test`,
 *    reached by a *dedicated non-superuser role with a password*. The
 *    production endpoint, every production database name, the `postgres`
 *    superuser, and an empty password (the `trust`-auth tell) are all
 *    forbidden values.
 *  - **RabbitMQ** has vhosts (a broker-enforced per-connection
 *    namespace), so a QA RabbitMQ is a *non-`/` vhost* reached by a user
 *    that holds *no permission on `/`* — the same broker is acceptable
 *    (`docs/RABBITMQ_TEST_ISOLATION.md`). The `/` vhost and the `guest`
 *    user are forbidden values; the pilot VPS host is forbidden at any
 *    port.
 */
object CoreQaSafetyGate {

    /** `host:port` pairs that are a production PostgreSQL endpoint. */
    val FORBIDDEN_POSTGRES_ENDPOINTS: Set<String> = setOf(
        // HOME-PC development machine — the single production PostgreSQL 18
        // instance (service postgresql-x64-18), also holding every `_test`
        // database. A QA PostgreSQL must NOT be this instance.
        "127.0.0.1:5432", "localhost:5432", "[::1]:5432", "::1:5432", "0.0.0.0:5432",
        // pilot VPS
        "62.217.176.214:5432"
    )

    /** Hosts that are production, at any port. */
    val FORBIDDEN_HOSTS: Set<String> = setOf("62.217.176.214", "piosapp.ru")

    /** Database names that are production (or another module's test/qa db) — never a Core QA target. */
    val PRODUCTION_POSTGRES_DATABASES: Set<String> = setOf(
        "pios_core",                       // Core's own production database — never the test target
        "pios_dispatch", "pios_order_management", "pios_identity",
        "pios_driver_management", "pios_passenger_experience", "pios_network_management",
        "pios", "postgres", "template0", "template1",
        "pios_dispatch_test", "pios_order_management_test", "pios_identity_test",
        "pios_driver_management_test", "pios_passenger_experience_test", "pios_network_management_test",
        "pios_driver_management_qa", "pios_identity_qa"
    )

    const val REQUIRED_QA_POSTGRES_DATABASE = "pios_core_test"

    /** PostgreSQL roles that must never be used by a Core QA connection. */
    val FORBIDDEN_POSTGRES_USERS: Set<String> = setOf("postgres")

    /** RabbitMQ vhosts that are production (or blank / the URL-encoded default). */
    val FORBIDDEN_RABBITMQ_VHOSTS: Set<String> = setOf("/", "", " ", "%2f", "%2F")

    /** RabbitMQ users that must never be used by a Core QA connection. */
    val FORBIDDEN_RABBITMQ_USERS: Set<String> = setOf("guest")

    // -------------------------------------------------------------------

    fun validatePostgres(url: String?, username: String?, password: String?) {
        if (url.isNullOrBlank()) {
            fail(
                "QA PostgreSQL is not configured. Set -Dpios.core.qa.postgres.url / " +
                    ".username / .password (or the PIOS_CORE_QA_POSTGRES_* env vars). " +
                    "Absent QA configuration = FAIL — there is no production default."
            )
        }
        val parsed = parseJdbcPostgres(url)
            ?: fail(
                "QA PostgreSQL url '$url' is not a parseable " +
                    "jdbc:postgresql://<host>:<port>/<database> value (an explicit port is required). " +
                    "Ambiguous configuration = FAIL."
            )
        val (host, port, database) = parsed

        if ("$host:$port" in FORBIDDEN_POSTGRES_ENDPOINTS) {
            fail("QA PostgreSQL endpoint '$host:$port' is a production PostgreSQL endpoint. FAIL — a QA PostgreSQL must be a distinct instance on a distinct port.")
        }
        if (host in FORBIDDEN_HOSTS) {
            fail("QA PostgreSQL host '$host' is a production host. FAIL.")
        }
        if (database in PRODUCTION_POSTGRES_DATABASES) {
            fail("QA PostgreSQL database '$database' is a production (or another module's) database name. FAIL.")
        }
        if (database != REQUIRED_QA_POSTGRES_DATABASE) {
            fail("QA PostgreSQL database must be exactly '$REQUIRED_QA_POSTGRES_DATABASE' (got '$database'). FAIL.")
        }
        if (username.isNullOrBlank()) {
            fail("QA PostgreSQL username is not configured. Absent QA configuration = FAIL.")
        }
        if (username in FORBIDDEN_POSTGRES_USERS) {
            fail("QA PostgreSQL username '$username' is a production superuser. FAIL — use a dedicated non-superuser QA role.")
        }
        if (password.isNullOrEmpty()) {
            fail(
                "QA PostgreSQL password is empty. FAIL — an empty password is the production instance's " +
                    "`trust`-auth tell; a dedicated QA role must authenticate with a password."
            )
        }
    }

    fun validateRabbitMq(host: String?, port: Int?, virtualHost: String?, username: String?) {
        if (host.isNullOrBlank()) {
            fail("QA RabbitMQ host is not configured. Absent QA configuration = FAIL.")
        }
        if (port == null || port <= 0 || port > 65535) {
            fail("QA RabbitMQ port is not configured or invalid ('$port'). Ambiguous configuration = FAIL.")
        }
        if (host in FORBIDDEN_HOSTS) {
            fail("QA RabbitMQ host '$host' is a production host. FAIL.")
        }
        if (virtualHost == null || virtualHost in FORBIDDEN_RABBITMQ_VHOSTS) {
            fail("QA RabbitMQ vhost '${virtualHost}' is the production vhost '/' (or blank). FAIL — the QA vhost must be a non-'/' namespace reached by a user with no permission on '/'.")
        }
        if (username.isNullOrBlank()) {
            fail("QA RabbitMQ username is not configured. Absent QA configuration = FAIL.")
        }
        if (username in FORBIDDEN_RABBITMQ_USERS) {
            fail("QA RabbitMQ username '$username' is the production 'guest' user. FAIL.")
        }
    }

    // -------------------------------------------------------------------

    private fun parseJdbcPostgres(url: String): Triple<String, Int, String>? {
        // jdbc:postgresql://host:port/database[?params][;params]
        val m = Regex("""^\s*jdbc:postgresql://([^:/?\s]+):(\d{1,5})/([^?/;\s]+)""").find(url) ?: return null
        val port = m.groupValues[2].toIntOrNull() ?: return null
        return Triple(m.groupValues[1], port, m.groupValues[3])
    }

    private fun fail(message: String): Nothing = throw QaSafetyViolation("Core QA Safety Gate: $message")
}
