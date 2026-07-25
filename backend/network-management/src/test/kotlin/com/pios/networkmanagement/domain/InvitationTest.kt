package com.pios.networkmanagement.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvitationTest {
    private fun newInvitation() = Invitation(
        id = InvitationId("invitation-1"),
        creatorPersonId = PersonId("artur"),
        code = "ABC123",
        createdAt = Instant.now()
    )

    @Test
    fun `a new invitation starts CREATED`() {
        assertEquals(InvitationStatus.CREATED, newInvitation().status)
    }

    @Test
    fun `using an invitation marks it USED`() {
        val invitation = newInvitation()
        invitation.use()
        assertEquals(InvitationStatus.USED, invitation.status)
    }

    @Test
    fun `using an already-used invitation fails`() {
        val invitation = newInvitation()
        invitation.use()
        assertFailsWith<IllegalStateException> { invitation.use() }
    }
}
