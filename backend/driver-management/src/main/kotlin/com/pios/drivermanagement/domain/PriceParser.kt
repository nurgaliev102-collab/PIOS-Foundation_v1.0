package com.pios.drivermanagement.domain

/**
 * ADR-065 (Driver Earnings from Self-Stated Prices), Decision item 4 —
 * confirmed by the product owner as written: pure computation of whether a
 * driver's own stated price (Dispatch's
 * [com.pios.dispatch.domain.Proposal.statedPrice], forwarded verbatim and
 * unparsed by Dispatch itself, per `ADR-042` R4.2) can be read as a bare
 * number.
 *
 * Kept free of any persistence/transport dependency so it is unit-testable
 * on its own, mirroring this module's own [RideStreakCalculator] precedent.
 *
 * The rule (not this implementer's choice — the product owner's, confirmed
 * 2026-09-07): a stated price contributes to the sum only if, after
 * trimming, it consists **entirely of digits**, optionally with internal
 * spaces acting as thousand separators (removed before parsing). Anything
 * else — «договоримся», «350 руб», «~400», «350.50», a blank or absent
 * value — parses to `null` and is never summed; the caller is responsible
 * for counting those separately (`unpricedRidesCount`).
 */
object PriceParser {

    /**
     * Returns the parsed amount for [statedPrice], or `null` if it does not
     * satisfy this rule. `null` in, `null` out — an absent stated price is
     * "no amount was ever stated," never zero (`ADR-042` R4.1's own
     * "absent input means `null`, not zero").
     */
    fun parse(statedPrice: String?): Long? {
        if (statedPrice == null) return null
        val trimmed = statedPrice.trim()
        if (trimmed.isEmpty()) return null
        val withoutInternalSpaces = trimmed.replace(" ", "")
        if (withoutInternalSpaces.isEmpty() || !withoutInternalSpaces.all { it.isDigit() }) return null
        return withoutInternalSpaces.toLongOrNull()
    }
}
