package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProposalIdTest {

    @Test
    fun `a blank proposal id is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            ProposalId("")
        }
    }
}
