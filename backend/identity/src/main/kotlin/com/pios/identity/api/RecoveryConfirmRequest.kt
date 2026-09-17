package com.pios.identity.api

/**
 * ADR-082 (D-03.6 items 3/4) — deliberately has no `driverId` field. See
 * [com.pios.identity.application.ConfirmRecoveryCommand]'s own KDoc.
 */
data class RecoveryConfirmRequest(val phone: String, val code: String, val newPassword: String)
