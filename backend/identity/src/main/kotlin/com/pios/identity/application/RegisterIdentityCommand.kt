package com.pios.identity.application

/**
 * [phone] and [password] are plain strings at the application-layer
 * boundary, the same "primitive in, domain type constructed inside"
 * convention [CreateIdentityCommand]'s own KDoc already uses. [phone]
 * validation happens once, inside [com.pios.identity.domain.Phone]'s own
 * constructor; [password] has no format beyond non-blank (ADR-055 invents
 * no password-complexity rule).
 */
data class RegisterIdentityCommand(val phone: String, val password: String)
