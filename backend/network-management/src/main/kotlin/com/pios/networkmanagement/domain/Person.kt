package com.pios.networkmanagement.domain

import java.time.Instant

/**
 * The Person aggregate (Sprint 7A: PIOS Network Foundation) — a human
 * participant's identity, independent of any single role (driver,
 * passenger, or otherwise). A Person's roles are represented separately by
 * [PersonProfile], never as fields on this class (a person may hold more
 * than one).
 *
 * [phone] is optional: nothing in Sprint 7A's own scope requires it, and
 * requiring it here would invent a validation rule this sprint was not
 * asked to enforce.
 */
class Person(
    val id: PersonId,
    val name: String,
    val phone: String?,
    val createdAt: Instant,
    val identityId: String? = null,
    val identityBoundAt: Instant? = null
) {
    init {
        require(name.isNotBlank()) { "Person name must not be blank" }
        require((identityId == null) == (identityBoundAt == null)) { "Identity binding must be complete" }
        require(identityId == null || identityId.isNotBlank()) { "IdentityId must not be blank" }
    }
}
