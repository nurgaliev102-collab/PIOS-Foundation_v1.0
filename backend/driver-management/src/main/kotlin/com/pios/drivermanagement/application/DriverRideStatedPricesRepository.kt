package com.pios.drivermanagement.application

import com.pios.drivermanagement.domain.DriverId

/**
 * ADR-065 (Driver Earnings from Self-Stated Prices), Decision item 3: "Both
 * the verbatim string and the parsed amount are stored per ride." Storage
 * boundary for that per-ride record — one row per completed ride this
 * module has seen an `AssignmentCompleted` for, keyed by that event's own
 * [eventId] (the same key [AssignmentCompletedRepository] already uses for
 * its own idempotency ledger).
 *
 * A derived read model, not a second source of truth: Dispatch's own
 * `proposals.stated_price` remains authoritative (ADR-065 Decision item 3's
 * own "not a second source of truth"). Keeping both the verbatim string and
 * the parsed amount here is what makes
 * [com.pios.drivermanagement.domain.PriceParser]'s own parsing rule
 * revisable later by rebuilding `driver_milestones`' two totals from this
 * table alone, with no Dispatch replay.
 */
interface DriverRideStatedPricesRepository {
    /**
     * Records [statedPriceRaw] (the driver's own stated price, forwarded
     * verbatim from Dispatch — `null` when no Proposal ever carried one)
     * and [statedPriceParsed] (the same value already parsed by
     * [com.pios.drivermanagement.domain.PriceParser] — `null` for a value
     * that did not parse) for [driverId]'s completed ride identified by
     * [eventId]. Idempotency (never calling this twice for the same
     * [eventId]) is [AssignmentCompletedApplicationService]'s own
     * responsibility, mirroring [DriverMilestonesRepository.recordCompletedRide]'s
     * own precedent — not this repository's.
     */
    fun record(eventId: String, driverId: DriverId, statedPriceRaw: String?, statedPriceParsed: Long?)
}
