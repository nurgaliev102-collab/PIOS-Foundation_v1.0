package com.pios.networkmanagement.domain

import java.time.Instant

/**
 * A single role held by a [Person] (Sprint 7A: PIOS Network Foundation).
 * A person with two roles (for example, `DRIVER` and `NETWORK_MEMBER`) is
 * represented as two separate `PersonProfile` rows sharing the same
 * [personId] — this class has no knowledge of a person's other profiles.
 */
class PersonProfile(
    val id: PersonProfileId,
    val personId: PersonId,
    val type: ProfileType,
    val createdAt: Instant
)
