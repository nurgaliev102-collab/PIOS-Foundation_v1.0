package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProposalMessageTest {

    private val proposalId = ProposalId("proposal-1")

    @Test
    fun `sending a message trims surrounding whitespace`() {
        val message = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "  Буду через 5 минут  ")

        assertEquals("Буду через 5 минут", message.body)
    }

    @Test
    fun `sending a blank message is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "   ")
        }
    }

    @Test
    fun `sending a message at exactly the length limit succeeds`() {
        val body = "a".repeat(ProposalMessage.MAX_BODY_LENGTH)

        val message = ProposalMessage.send(proposalId, MessageSenderRole.DRIVER, body)

        assertEquals(ProposalMessage.MAX_BODY_LENGTH, message.body.length)
    }

    @Test
    fun `sending a message longer than the length limit is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ProposalMessage.send(proposalId, MessageSenderRole.DRIVER, "a".repeat(ProposalMessage.MAX_BODY_LENGTH + 1))
        }
    }

    @Test
    fun `each sent message gets a distinct id`() {
        val first = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "Первое")
        val second = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "Первое")

        assertEquals(false, first.id == second.id)
    }

    @Test
    fun `a message carries the sender role it was sent with`() {
        val passengerMessage = ProposalMessage.send(proposalId, MessageSenderRole.PASSENGER, "От пассажира")
        val driverMessage = ProposalMessage.send(proposalId, MessageSenderRole.DRIVER, "От водителя")

        assertEquals(MessageSenderRole.PASSENGER, passengerMessage.senderRole)
        assertEquals(MessageSenderRole.DRIVER, driverMessage.senderRole)
    }
}
