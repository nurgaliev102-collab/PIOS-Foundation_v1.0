package com.pios.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AssignmentIdTest {

    @Test
    fun `a blank assignment id is not valid`() {
        assertFailsWith<IllegalArgumentException> {
            AssignmentId("")
        }
    }
}
