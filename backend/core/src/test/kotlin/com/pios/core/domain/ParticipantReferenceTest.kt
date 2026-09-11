package com.pios.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParticipantReferenceTest {

    @Test
    fun `a blank participant reference is not valid`() {
        assertFailsWith<IllegalArgumentException> { ParticipantReference("") }
        assertFailsWith<IllegalArgumentException> { ParticipantReference("   ") }
    }

    @Test
    fun `it is an opaque wrapper over its value - never interpreted`() {
        // ADR-067 Participant Reference: Core neither mints references nor
        // interprets their structure. Any non-blank string is accepted as
        // an identityId, a driver id, or anything else — the type carries
        // no claim about a canonical Person ID.
        assertEquals("identity-abc", ParticipantReference("identity-abc").value)
        assertEquals("driver-123", ParticipantReference("driver-123").value)
    }
}
