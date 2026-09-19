package com.pios.dispatch.api

/**
 * The REST request body for Register Driver Push Subscription (ADR-083,
 * D-10). [driverId]/[driverReference] exist on this type only so a
 * client-supplied driver-identifying field can be detected and rejected
 * (400) rather than silently ignored -- [DriverPushSubscriptionController.register]
 * never reads either field's value; the subscribing driver always comes
 * from the caller's own verified `Bearer` token instead (ADR-083 Part 8:
 * "One driver cannot register or delete under another driver's identity").
 */
data class RegisterDriverPushSubscriptionRequest(
    val endpoint: String? = null,
    val keys: Keys? = null,
    val driverId: String? = null,
    val driverReference: String? = null
) {
    data class Keys(val p256dh: String? = null, val auth: String? = null)
}
