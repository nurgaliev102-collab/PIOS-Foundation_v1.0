package com.pios.drivermanagement.api

/**
 * The REST request body for Update Long-Distance Preference (PIOS Group
 * and Long-Distance Rides Roadmap, Stage 3).
 */
data class UpdateLongDistancePreferenceRequest(
    val accepts: Boolean
)
