package com.pios.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards ADR-067's closed vocabulary: Slice 01 records facts derived only
 * from the five Current Authorized Inputs. If a future slice adds a kind,
 * this test is the deliberate checkpoint that forces a reviewer to
 * confirm it maps to an authorized input — not a Reserved Future Input,
 * and never `DriverAvailabilityChanged`.
 */
class HistoryEventKindTest {

    @Test
    fun `the vocabulary is exactly the six Slice 01 kinds`() {
        assertEquals(
            setOf(
                "ORDER_SUBMITTED",
                "ORDER_COMPLETED",
                "ORDER_CANCELLED",
                "RIDE_COMPLETED_AS_DRIVER",
                "PRIMARY_DRIVER_DESIGNATED",
                "DESIGNATED_AS_PRIMARY_DRIVER"
            ),
            HistoryEventKind.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `no kind derives from DriverAvailabilityChanged or any Reserved Future Input`() {
        val names = HistoryEventKind.entries.map { it.name }
        // These substrings would signal a leak of an excluded/reserved
        // concept into the vocabulary.
        listOf("AVAILAB", "OFFLINE", "ONLINE", "PROPOSAL", "TRIP", "ARRIVED", "STARTED", "CLEARED", "ASSIGNED")
            .forEach { forbidden ->
                assert(names.none { it.contains(forbidden) }) {
                    "HistoryEventKind must not contain a kind mentioning '$forbidden' in Slice 01"
                }
            }
    }
}
