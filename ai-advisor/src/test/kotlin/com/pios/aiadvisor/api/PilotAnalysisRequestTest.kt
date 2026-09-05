package com.pios.aiadvisor.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * PIOS Intelligence Trend Context — proves [PilotAnalysisRequest.currentDay]/
 * [PilotAnalysisRequest.history] are genuinely optional at the JSON boundary
 * `AdvisorController.analyze` deserializes through
 * (`@RequestBody request: PilotAnalysisRequest`), not just at the Kotlin
 * constructor-default level [AdvisorControllerTest] already exercises. A
 * request body from a frontend build that predates these fields must keep
 * deserializing exactly as before -- this task's own explicit
 * backward-compatibility requirement.
 */
class PilotAnalysisRequestTest {

    private val mapper = jacksonObjectMapper()

    private val bodyWithoutHistory = """
        {
          "generatedAt": "2026-08-17T12:00:00.000Z",
          "periodLabel": "test",
          "orders": {"total": 4, "completed": 3, "cancelled": 1, "open": 0},
          "proposals": {"total": 4, "accepted": 3, "declined": 0, "lapsed": 1, "withdrawn": 0, "open": 0},
          "assignments": {"total": 3, "completed": 3, "inProgress": 0},
          "drivers": {"total": 2, "available": 1, "withActivity": 2},
          "reactionTime": {"averageMinutes": null, "medianMinutes": null, "sampleSize": 0},
          "health": {"modulesUp": 5, "modulesTotal": 5}
        }
    """.trimIndent()

    @Test
    fun `a request body with no history field at all deserializes with history null, same as any old frontend build`() {
        val request = mapper.readValue(bodyWithoutHistory, PilotAnalysisRequest::class.java)

        assertNull(request.history)
        assertNull(request.currentDay)
        assertEquals(4, request.orders.total)
    }

    @Test
    fun `a request body carrying history deserializes it into real DailySnapshot values`() {
        val bodyWithHistory = """
            {
              "generatedAt": "2026-08-17T12:00:00.000Z",
              "periodLabel": "test",
              "orders": {"total": 18, "completed": 14, "cancelled": 3, "open": 1},
              "proposals": {"total": 18, "accepted": 14, "declined": 1, "lapsed": 0, "withdrawn": 0, "open": 3},
              "assignments": {"total": 14, "completed": 14, "inProgress": 0},
              "drivers": {"total": 5, "available": 2, "withActivity": 5},
              "reactionTime": {"averageMinutes": null, "medianMinutes": null, "sampleSize": 0},
              "health": {"modulesUp": 5, "modulesTotal": 5},
              "history": [
                {
                  "date": "2026-08-16",
                  "orders": {"total": 12, "completed": 9, "cancelled": 2, "open": 0},
                  "proposals": {"total": 12, "accepted": 9, "declined": 1, "lapsed": 0, "withdrawn": 0, "open": 0},
                  "assignments": {"total": 9, "completed": 9, "inProgress": 0},
                  "activeDrivers": 4
                }
              ]
            }
        """.trimIndent()

        val request = mapper.readValue(bodyWithHistory, PilotAnalysisRequest::class.java)

        val history = request.history!!
        assertEquals(1, history.size)
        assertEquals("2026-08-16", history.first().date)
        assertEquals(12, history.first().orders.total)
        assertEquals(4, history.first().activeDrivers)
    }

    @Test
    fun `a request body carrying currentDay deserializes it separately from history and from the ALL-PERIOD orders field`() {
        val bodyWithCurrentDay = """
            {
              "generatedAt": "2026-08-17T15:00:00.000Z",
              "periodLabel": "test",
              "orders": {"total": 18, "completed": 14, "cancelled": 3, "open": 1},
              "proposals": {"total": 18, "accepted": 14, "declined": 1, "lapsed": 0, "withdrawn": 0, "open": 3},
              "assignments": {"total": 14, "completed": 14, "inProgress": 0},
              "drivers": {"total": 5, "available": 2, "withActivity": 5},
              "reactionTime": {"averageMinutes": null, "medianMinutes": null, "sampleSize": 0},
              "health": {"modulesUp": 5, "modulesTotal": 5},
              "currentDay": {
                "date": "2026-08-17",
                "orders": {"total": 6, "completed": 4, "cancelled": 1, "open": 1},
                "proposals": {"total": 6, "accepted": 4, "declined": 1, "lapsed": 0, "withdrawn": 0, "open": 1},
                "assignments": {"total": 4, "completed": 4, "inProgress": 0},
                "activeDrivers": 2
              }
            }
        """.trimIndent()

        val request = mapper.readValue(bodyWithCurrentDay, PilotAnalysisRequest::class.java)

        assertEquals(18, request.orders.total) // ALL-PERIOD, unaffected
        assertEquals("2026-08-17", request.currentDay!!.date)
        assertEquals(6, request.currentDay!!.orders.total) // CURRENT DAY, distinct from the 18 above
        assertNull(request.history)
    }

    @Test
    fun `an explicit empty history array deserializes to an empty list, not null`() {
        val bodyWithEmptyHistory = """
            {
              "generatedAt": "2026-08-17T12:00:00.000Z",
              "periodLabel": "test",
              "orders": {"total": 1, "completed": 1, "cancelled": 0, "open": 0},
              "proposals": {"total": 1, "accepted": 1, "declined": 0, "lapsed": 0, "withdrawn": 0, "open": 0},
              "assignments": {"total": 1, "completed": 1, "inProgress": 0},
              "drivers": {"total": 1, "available": 1, "withActivity": 1},
              "reactionTime": {"averageMinutes": null, "medianMinutes": null, "sampleSize": 0},
              "health": {"modulesUp": 5, "modulesTotal": 5},
              "history": []
            }
        """.trimIndent()

        val request = mapper.readValue(bodyWithEmptyHistory, PilotAnalysisRequest::class.java)

        assertEquals(emptyList(), request.history)
    }
}
