package com.pios.identity.domain

import java.time.Instant

/**
 * Documents the future shape of "one device/browser an [Identity] has
 * registered" (ADR-038, Этап 3) — for example, the device a passkey would
 * eventually be bound to. Unpersisted and unreferenced by any application
 * service today; a [DeviceId]-keyed table is deliberately not added until
 * something real would write to it — committing an empty schema for a
 * concept nothing uses yet would be exactly the kind of speculative
 * over-design `.claude/CLAUDE.md`'s own engineering principles warn
 * against ("Avoid speculative refactoring; unnecessary abstractions").
 */
data class Device(
    val id: DeviceId,
    val identityId: IdentityId,
    val label: String,
    val registeredAt: Instant
)

@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId must not be blank" }
    }
}
