package com.pios.identity.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Mints the signed session token ADR-055 Decision 1 specifies:
 * `<base64url(payload)>.<base64url(HMAC-SHA256(secret, base64url(payload)))>`,
 * payload `{"sub":"<identityId>","drv":"<driverId>"|null,"exp":<epochSeconds>}`.
 * `identity` is the sole issuer in the platform (ADR-055 Decision 1) —
 * every other module only ever verifies a token this class minted, using
 * its own replicated `SessionTokenVerifier`
 * (`com.pios.identity.api.SessionTokenVerifier`'s own KDoc explains why
 * *that* class, not this one, is the one replicated per module).
 *
 * `pios.session.ttl-seconds` defaults to 2 592 000 (30 days), per ADR-055.
 * `pios.session.secret` (base64) must be identical, byte for byte, to
 * every verifying module's own `pios.session.secret` — ADR-055's own
 * disclosed Consequence ("a stolen token is valid until it expires... The
 * HMAC secret is shared between modules").
 *
 * `sessionGeneration` (ADR-082, D-03.3) is carried as the additive `sgen`
 * claim, the same "absence reads as the conservative default" precedent
 * `ADR-075`'s own `gst` claim already established. Every real caller
 * passes the identity's own *current* `sessionGeneration` at mint time —
 * see each call site's own KDoc for why. See `SessionTokenVerifier`'s own
 * KDoc, and ADR-082 §8, for the disclosed scope of what this claim
 * actually invalidates and where.
 */
@Component
class SessionTokenIssuer(
    @Value("\${pios.session.secret:}") private val secretBase64: String,
    @Value("\${pios.session.ttl-seconds:2592000}") private val ttlSeconds: Long,
    @Value("\${pios.session.guest-ttl-seconds:604800}") private val guestTtlSeconds: Long = 604_800
) {
    private val objectMapper = ObjectMapper()

    data class IssuedToken(val token: String, val expiresAt: Instant)

    /**
     * Mints a token for [identityId], carrying [driverId] as the `drv`
     * claim (`null` for an Identity with no associated driver, per
     * ADR-055 Decision 6) and [sessionGeneration] as the `sgen` claim
     * (ADR-082). Throws [IllegalStateException] if `pios.session.secret`
     * is unset — a deployment that can register or log in a caller must
     * configure the secret; there is no "issue an unusable token" fallback.
     */
    fun issue(identityId: String, driverId: String?, sessionGeneration: Int = 0): IssuedToken =
        issue(identityId, driverId, guest = false, ttlSeconds = ttlSeconds, sessionGeneration = sessionGeneration)

    fun issueGuest(identityId: String, sessionGeneration: Int = 0): IssuedToken =
        issue(identityId, driverId = null, guest = true, ttlSeconds = guestTtlSeconds, sessionGeneration = sessionGeneration)

    private fun issue(identityId: String, driverId: String?, guest: Boolean, ttlSeconds: Long, sessionGeneration: Int): IssuedToken {
        check(secretBase64.isNotBlank()) { "pios.session.secret must be configured to issue a session token" }
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        val payloadNode = objectMapper.createObjectNode()
        payloadNode.put("sub", identityId)
        if (driverId == null) {
            payloadNode.putNull("drv")
        } else {
            payloadNode.put("drv", driverId)
        }
        payloadNode.put("gst", guest)
        payloadNode.put("sgen", sessionGeneration)
        payloadNode.put("exp", expiresAt.epochSecond)
        val encodedPayload = base64UrlEncode(objectMapper.writeValueAsBytes(payloadNode))
        val encodedSignature = base64UrlEncode(hmac(encodedPayload))
        return IssuedToken("$encodedPayload.$encodedSignature", expiresAt)
    }

    private fun hmac(data: String): ByteArray {
        val secretBytes = Base64.getDecoder().decode(secretBase64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun base64UrlEncode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
