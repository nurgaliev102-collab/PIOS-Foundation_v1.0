package com.pios.drivermanagement.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ADR-065, Decision item 4 (confirmed by the product owner, 2026-09-07):
 * proves [PriceParser]'s digits-only-after-trimming rule in isolation,
 * mirroring [RideStreakCalculatorTest]'s own precedent for testing a pure
 * domain function without any persistence/transport dependency.
 */
class PriceParserTest {

    @Test
    fun `a bare digit string parses to its own numeric value`() {
        assertEquals(350L, PriceParser.parse("350"))
    }

    @Test
    fun `leading and trailing whitespace is trimmed before parsing`() {
        assertEquals(350L, PriceParser.parse("  350  "))
    }

    @Test
    fun `internal spaces are removed as thousand separators`() {
        assertEquals(12500L, PriceParser.parse("12 500"))
    }

    @Test
    fun `a currency suffix makes the value unparseable`() {
        assertNull(PriceParser.parse("350 руб"))
    }

    @Test
    fun `a non-numeric phrase is unparseable`() {
        assertNull(PriceParser.parse("договоримся"))
    }

    @Test
    fun `an approximate value with a tilde is unparseable`() {
        assertNull(PriceParser.parse("~400"))
    }

    @Test
    fun `a decimal value is unparseable`() {
        assertNull(PriceParser.parse("350.50"))
    }

    @Test
    fun `an empty string is unparseable`() {
        assertNull(PriceParser.parse(""))
    }

    @Test
    fun `a blank (whitespace-only) string is unparseable`() {
        assertNull(PriceParser.parse("   "))
    }

    @Test
    fun `a string that is only internal spaces is unparseable`() {
        assertNull(PriceParser.parse(" "))
    }

    @Test
    fun `null parses to null`() {
        assertNull(PriceParser.parse(null))
    }

    @Test
    fun `a negative number is unparseable -- the leading minus is not a digit`() {
        assertNull(PriceParser.parse("-350"))
    }
}
