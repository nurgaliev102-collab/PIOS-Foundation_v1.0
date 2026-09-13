package com.pios.dispatch.persistence

import com.pios.dispatch.application.ProposalMessageRepository
import com.pios.dispatch.domain.MessageSenderRole
import com.pios.dispatch.domain.ProposalId
import com.pios.dispatch.domain.ProposalMessage
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A shared behavioral contract every [ProposalMessageRepository]
 * implementation must satisfy, run identically against
 * [InMemoryProposalMessageRepository] and
 * [PostgreSQLProposalMessageRepository] -- mirrors
 * [ProposalRepositoryContractTest] exactly.
 *
 * Every [ProposalId] used here is freshly randomized per test (never a
 * fixed literal): unlike [Proposal]'s/[Order]'s own `save()`, which
 * upserts by id (`ON CONFLICT ... DO UPDATE`) and so tolerates a fixed
 * literal id across repeated runs against a real, persistent Postgres
 * test database, [ProposalMessage] is plain-INSERT, append-only, and
 * looked up by [ProposalMessage.proposalId] rather than its own [id] — a
 * fixed literal proposal id would silently accumulate one extra row every
 * time this suite runs against the same real database, eventually failing
 * assertions that expect an exact count/order for that id. Randomizing
 * sidesteps this without needing any cleanup step.
 */
abstract class ProposalMessageRepositoryContractTest {

    abstract fun createRepository(): ProposalMessageRepository

    private fun freshProposalId(): ProposalId = ProposalId("contract-test-proposal-${UUID.randomUUID()}")

    @Test
    fun `a saved message can be found by its own proposal id`() {
        val repository = createRepository()
        val proposalId = freshProposalId()
        val message = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "Буду у подъезда")

        repository.save(message)

        val found = repository.findByProposal(proposalId)
        assertEquals(1, found.size)
        assertEquals("Буду у подъезда", found[0].body)
        assertEquals(MessageSenderRole.PASSENGER, found[0].senderRole)
    }

    @Test
    fun `a proposal with no messages returns an empty list, not null or an error`() {
        val repository = createRepository()

        assertEquals(emptyList(), repository.findByProposal(freshProposalId()))
    }

    @Test
    fun `messages for a different proposal are never returned`() {
        val repository = createRepository()
        val proposalA = freshProposalId()
        val proposalB = freshProposalId()
        repository.save(ProposalMessage.send(proposalA, MessageSenderRole.PASSENGER, "Для A"))
        repository.save(ProposalMessage.send(proposalB, MessageSenderRole.DRIVER, "Для B"))

        val foundA = repository.findByProposal(proposalA)
        val foundB = repository.findByProposal(proposalB)

        assertEquals(listOf("Для A"), foundA.map { it.body })
        assertEquals(listOf("Для B"), foundB.map { it.body })
    }

    @Test
    fun `multiple messages on the same proposal all survive, oldest first`() {
        val repository = createRepository()
        val proposalId = freshProposalId()
        val first = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "Первое", at = java.time.Instant.parse("2026-09-01T10:00:00Z"))
        val second = ProposalMessage.send(proposalId, MessageSenderRole.DRIVER, "Второе", at = java.time.Instant.parse("2026-09-01T10:01:00Z"))
        repository.save(second)
        repository.save(first)

        val found = repository.findByProposal(proposalId)

        assertEquals(listOf("Первое", "Второе"), found.map { it.body })
    }

    @Test
    fun `a message saved with a sender role round-trips exactly`() {
        val repository = createRepository()
        val proposalId = freshProposalId()
        repository.save(ProposalMessage.send(proposalId, MessageSenderRole.DRIVER, "От водителя"))

        val found = repository.findByProposal(proposalId)

        assertEquals(MessageSenderRole.DRIVER, found.single().senderRole)
        assertTrue(found.single().id.value.isNotBlank())
    }
}

class InMemoryProposalMessageRepositoryContractTest : ProposalMessageRepositoryContractTest() {
    override fun createRepository(): ProposalMessageRepository = InMemoryProposalMessageRepository()
}

class PostgreSQLProposalMessageRepositoryContractTest : ProposalMessageRepositoryContractTest() {
    override fun createRepository(): ProposalMessageRepository =
        PostgreSQLProposalMessageRepository(JdbcTemplate(PostgreSQLTestDatabase.dataSource))
}
