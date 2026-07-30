package com.pios.identity.application

/**
 * [phone] is a plain string, not a [com.pios.identity.domain.Phone], at the
 * application-layer boundary — the same "primitive in, domain type
 * constructed inside" convention every other module's own Create*Command
 * already uses (e.g. `com.pios.networkmanagement.application.CreatePersonCommand`).
 * Validation happens once, inside [com.pios.identity.domain.Phone]'s own
 * constructor, not duplicated here.
 */
data class CreateIdentityCommand(val phone: String?)
