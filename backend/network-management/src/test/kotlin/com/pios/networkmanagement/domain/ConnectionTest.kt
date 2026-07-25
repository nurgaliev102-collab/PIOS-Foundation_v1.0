package com.pios.networkmanagement.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConnectionTest {
    @Test
    fun `a Connection cannot link a Person to themselves`() {
        val personId = PersonId("same-person")
        assertFailsWith<IllegalArgumentException> {
            Connection(
                id = ConnectionId("connection-1"),
                fromPersonId = personId,
                toPersonId = personId,
                type = ConnectionType.CONNECTED,
                createdAt = Instant.now()
            )
        }
    }
}
