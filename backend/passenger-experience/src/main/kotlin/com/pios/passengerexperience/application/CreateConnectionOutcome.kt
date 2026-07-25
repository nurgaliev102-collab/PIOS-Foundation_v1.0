package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection

/**
 * The result of [CreateConnectionApplicationService.handle] -- [created]
 * distinguishes "this pairing did not exist before this call" (the
 * controller maps this to 201) from "this pairing already existed" (the
 * controller maps this to 200), per this sprint's own idempotent-link
 * requirement: opening the same invitation link twice must not be an
 * error.
 */
data class CreateConnectionOutcome(val connection: Connection, val created: Boolean)
