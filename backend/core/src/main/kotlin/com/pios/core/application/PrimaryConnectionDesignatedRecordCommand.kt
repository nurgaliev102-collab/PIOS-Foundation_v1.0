package com.pios.core.application

import java.time.Instant

/**
 * The primitive-typed command for a received `PrimaryConnectionDesignated`
 * message (passenger-experience, routing key
 * `primary.connection.designated`). The envelope's payload carries
 * `passengerReference` and `driverId` (verified against
 * `SetPrimaryConnectionApplicationService.outboxRecordFor`).
 *
 * Slice 01 records **two** facts from this one event (ADR-067 Input
 * Events: participant reference is `payload.passengerReference` *and*
 * `payload.driverId`):
 *  - `PRIMARY_DRIVER_DESIGNATED` for [passengerReference]
 *  - `DESIGNATED_AS_PRIMARY_DRIVER` for [driverReference]
 *
 * `PrimaryConnectionCleared` is a Reserved Future Input (ADR-067) and is
 * NOT consumed by Slice 01 — this command has no cleared counterpart.
 */
data class PrimaryConnectionDesignatedRecordCommand(
    val eventId: String,
    val passengerReference: String,
    val driverReference: String,
    val occurredAt: Instant
)
