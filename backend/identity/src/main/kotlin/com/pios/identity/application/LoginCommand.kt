package com.pios.identity.application

/**
 * [phone] and [password] are plain strings, exactly as presented — unlike
 * [RegisterIdentityCommand], login never lets a malformed [phone] surface
 * as a distinct failure mode (ADR-055 Decision 3: "never distinguishes
 * unknown phone from wrong password"), so no [com.pios.identity.domain.Phone]
 * construction happens at this boundary either.
 */
data class LoginCommand(val phone: String, val password: String)
