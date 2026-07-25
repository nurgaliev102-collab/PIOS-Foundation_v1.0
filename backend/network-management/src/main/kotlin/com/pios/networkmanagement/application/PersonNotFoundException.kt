package com.pios.networkmanagement.application

import com.pios.networkmanagement.domain.PersonId

/**
 * Thrown when an operation references a [PersonId] that identifies no
 * saved [com.pios.networkmanagement.domain.Person] — mapped to HTTP 404 by
 * every controller that can raise it, mirroring
 * `com.pios.drivermanagement.application.DriverNotFoundException`'s own
 * convention.
 */
class PersonNotFoundException(val personId: PersonId) : RuntimeException("Person not found: ${personId.value}")
