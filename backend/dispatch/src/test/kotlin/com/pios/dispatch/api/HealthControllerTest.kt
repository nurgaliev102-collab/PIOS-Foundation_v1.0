package com.pios.dispatch.api

import com.pios.dispatch.application.OutboxBacklog
import com.pios.dispatch.application.OutboxRecord
import com.pios.dispatch.application.OutboxRepository
import com.pios.dispatch.persistence.PostgreSQLTestDatabase
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Proves `GET /v1/health` (ADR-043, ADR-044) end to end against a real
 * database connection (for the `SELECT 1` datasource check), with a fake
 * [OutboxRepository] standing in for the outbox table — mirroring this
 * module's own constructor-based controller testing convention
 * ([HealthController] is constructed directly, no Spring MVC context).
 *
 * The specific test named by the design document and by ADR-044 Decision
 * 3 as the one easy-to-miss requirement: a `401` from this endpoint must
 * never carry a `WWW-Authenticate` header, or the browser raises its own
 * native credential dialog ahead of the console's own login screen.
 */
class HealthControllerTest {

    private val salt = "owner-test-salt".toByteArray()
    private val iterations = 1000
    private val password = "owner-password"
    private val passwordHash = deriveKey(password, salt, iterations)

    private val gate = OwnerCredentialGate(
        configuredUsername = "owner",
        configuredPasswordHash = Base64.getEncoder().encodeToString(passwordHash),
        configuredPasswordSalt = Base64.getEncoder().encodeToString(salt),
        iterations = iterations,
        failureDelayMillis = 0,
        maxFailuresPerWindow = 1000,
        windowMillis = 900_000
    )

    private val fakeOutboxRepository = object : OutboxRepository {
        override fun save(record: OutboxRecord): OutboxRecord = record
        override fun findUnpublished(): List<OutboxRecord> = emptyList()
        override fun markPublished(id: Long) = Unit
        override fun countUnpublished(): OutboxBacklog =
            OutboxBacklog(pending = 14, oldestPendingCreatedAt = Instant.now().minusSeconds(372))
    }

    private val controller = HealthController(
        gate,
        JdbcTemplate(PostgreSQLTestDatabase.dataSource),
        fakeOutboxRepository
    )

    private fun basicHeader(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())

    @Test
    fun `a correct credential returns 200 with this module's own health and no WWW-Authenticate header`() {
        val response = controller.health(basicHeader("owner", password))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.headers.getFirst("WWW-Authenticate"))
        val body = assertNotNull(response.body)
        assertEquals("dispatch", body.module)
        assertEquals("UP", body.status)
        assertEquals("UP", body.database)
        assertEquals(14, body.outbox?.pending)
        assertNotNull(body.outbox?.oldestPendingAgeSeconds)
    }

    @Test
    fun `a missing Authorization header returns 401 with no WWW-Authenticate header and no body`() {
        val response = controller.health(null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.headers.getFirst("WWW-Authenticate"))
        assertNull(response.body)
    }

    @Test
    fun `an incorrect credential returns 401 with no WWW-Authenticate header and no body`() {
        val response = controller.health(basicHeader("owner", "wrong-password"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(response.headers.getFirst("WWW-Authenticate"))
        assertNull(response.body)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
