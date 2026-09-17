package com.pios.identity.application

/**
 * ADR-082 §7 (D-03.7 closure pass) — thrown only by
 * [RequestPhoneVerificationApplicationService], whose caller is already
 * authenticated (Bearer-gated), so this may be distinguished from other
 * failures (mapped by [com.pios.identity.api.IdentityController] to 429,
 * the same status [com.pios.identity.application.OtpClientKeyRateLimiter]'s
 * own exhaustion already produces at the controller level). Never thrown by
 * [RequestRecoveryApplicationService] — that path is anonymous and
 * enumeration-sensitive, so a phone-budget exhaustion there stays silent
 * (D-03.7: rate limiting must never become an enumeration oracle).
 */
class TooManyOtpRequestsException : RuntimeException("too many OTP requests for this phone")
