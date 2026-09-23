package com.pios.networkmanagement.persistence

import com.pios.networkmanagement.application.CreatePersonApplicationService
import com.pios.networkmanagement.application.CreatePersonCommand
import com.pios.networkmanagement.domain.Person
import com.pios.networkmanagement.domain.PersonId
import org.flywaydb.core.Flyway
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses only the fixed pios_network_management_test database. */
class PostgreSQLPersonBindingSecurityTest {
    private val dataSource = PostgreSQLTestDatabase.dataSource
    private val jdbc = JdbcTemplate(dataSource)
    private val repo = PostgreSQLPersonRepository(jdbc)
    private val service = CreatePersonApplicationService(repo,
        SpringTransactionRunner(TransactionTemplate(DataSourceTransactionManager(dataSource))))

    @Test
    fun `parallel creation for one identity returns one Person and one row`() {
        val identity = "race-${UUID.randomUUID()}"
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = (1..2).map { pool.submit(Callable {
                start.await()
                service.createOrGet(CreatePersonCommand("Caller", null, identity))
            }) }
            start.countDown()
            val outcomes = tasks.map { it.get() }
            assertEquals(1, outcomes.count { it.created })
            assertEquals(outcomes[0].person.id, outcomes[1].person.id)
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM persons WHERE identity_id = ?", Int::class.java, identity))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `database rejects duplicate owner and any rebind or half binding`() {
        val identity = "owner-${UUID.randomUUID()}"
        val original = service.createOrGet(CreatePersonCommand("Caller", null, identity)).person
        assertFailsWith<DataAccessException> {
            jdbc.update("UPDATE persons SET identity_id = ? WHERE id = ?", "other-${UUID.randomUUID()}", original.id.value)
        }
        assertFailsWith<DataAccessException> {
            jdbc.update("UPDATE persons SET identity_bound_at = now() WHERE id = ?", original.id.value)
        }
        assertFailsWith<DataAccessException> {
            jdbc.update(
                "INSERT INTO persons (id, name, created_at, identity_id, identity_bound_at) VALUES (?, 'Other', now(), ?, now())",
                UUID.randomUUID().toString(), identity
            )
        }
        assertFailsWith<DataAccessException> {
            jdbc.update(
                "INSERT INTO persons (id, name, created_at, identity_id) VALUES (?, 'Half', now(), ?)",
                UUID.randomUUID().toString(), "half-${UUID.randomUUID()}"
            )
        }
        assertEquals(identity, assertNotNull(repo.findById(original.id)).identityId)
    }

    @Test
    fun `legacy Person remains unbound after repeated migration and cannot be claimed`() {
        val legacy = Person(PersonId("legacy-${UUID.randomUUID()}"), "Legacy", "+7999", Instant.now())
        repo.save(legacy)
        val before = assertNotNull(repo.findById(legacy.id))
        assertNull(before.identityId)
        assertNull(before.identityBoundAt)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/networkmanagement").load().migrate()
        val after = assertNotNull(repo.findById(legacy.id))
        assertNull(after.identityId)
        assertNull(after.identityBoundAt)
        assertFailsWith<DataAccessException> {
            jdbc.update("UPDATE persons SET identity_id = ?, identity_bound_at = now() WHERE id = ?",
                "claim-${UUID.randomUUID()}", legacy.id.value)
        }
        assertTrue(repo.findByIdentityId("claim-${UUID.randomUUID()}") == null)
    }
}
