package com.pios.identity.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies the signed session token ADR-055 Decision 1 mints
 * (`com.pios.identity.application.SessionTokenIssuer`) — checked
 * **locally**, with no call to `identity`, exactly as that Decision
 * specifies. Replicated identically into every verifying module —
 * `passenger-experience`'s own copy of this exact file, package line only
 * changed — the same shape and justification `OwnerCredentialGate`
 * already established for its own five-module replication
 * (`MODULE_STRUCTURE.md` Section 4 forbids shared business code between
 * modules).
 *
 * `pios.session.secret` (base64) must be identical, byte for byte, across
 * every module that verifies — issuing and verifying share one secret
 * (ADR-055's own disclosed Consequence). An unset secret makes every
 * verification return `null` — closed by default, the same posture as
 * `OwnerCredentialGate.isConfigured()`.
 */
@Component
class SessionTokenVerifier(
    @Value("\${pios.session.secret:}") private val secretBase64: String
) {
    private val objectMapper = ObjectMapper()

    /**
     * [sgen] (ADR-082, D-03.3) is the session generation this token was
     * minted at — additive, absent/non-number reads as `0`, the same
     * conservative-default precedent [guest]'s own `gst` claim already
     * established (`ADR-075`). Parsed here, in `identity`'s own copy of
     * this class only: the other five modules' replicated verifiers do
     * **not** parse or check this claim, and are not modified by ADR-082 —
     * see that ADR's §8 for the disclosed reason (no local access to the
     * live `Identity.sessionGeneration` value without reintroducing the
     * per-request cross-module coupling ADR-055 Decision 1 rejected).
     */
    data class VerifiedToken(val sub: String, val drv: String?, val guest: Boolean = false, val sgen: Int = 0)

    /**
     * Verifies [authorizationHeader] (the raw `Authorization` header
     * value, or `null` if absent) as a `Bearer` session token. Returns
     * `null` for anything not a currently-valid, correctly-signed,
     * unexpired token — missing header, wrong scheme, malformed token,
     * bad signature, and an expired token all collapse to the same
     * `null`, so a caller cannot distinguish the reason from the return
     * value alone (mirrors `OwnerCredentialGate`'s own "never
     * distinguish" discipline).
     */
    fun verify(authorizationHeader: String?): VerifiedToken? {
        if (secretBase64.isBlank()) {
            return null
        }
        val token = extractBearerToken(authorizationHeader) ?: return null
        val parts = token.split(".")
        if (parts.size != 2) {
            return null
        }
        val (encodedPayload, encodedSignature) = parts
        val presentedSignature = base64UrlDecodeOrNull(encodedSignature) ?: return null
        val expectedSignature = hmacOrNull(encodedPayload) ?: return null
        if (!MessageDigest.isEqual(expectedSignature, presentedSignature)) {
            return null
        }
        val payloadBytes = base64UrlDecodeOrNull(encodedPayload) ?: return null
        val payloadNode = try {
            objectMapper.readTree(payloadBytes)
        } catch (ex: Exception) {
            null
        } ?: return null

        val sub = payloadNode.get("sub")?.takeIf { it.isTextual }?.asText()
        if (sub.isNullOrBlank()) {
            return null
        }
        val drvNode = payloadNode.get("drv")
        val drv = if (drvNode == null || drvNode.isNull) null else drvNode.asText()
        val expNode = payloadNode.get("exp")
        if (expNode == null || !expNode.isNumber) {
            return null
        }
        if (expNode.asLong() <= Instant.now().epochSecond) {
            return null
        }
        val guest = payloadNode.get("gst")?.takeIf { it.isBoolean }?.asBoolean() ?: false
        val sgen = payloadNode.get("sgen")?.takeIf { it.isNumber }?.asInt() ?: 0
        return VerifiedToken(sub, drv, guest, sgen)
    }

    private fun extractBearerToken(header: String?): String? {
        if (header == null || !header.startsWith("Bearer ")) {
            return null
        }
        return header.removePrefix("Bearer ").trim().ifBlank { null }
    }

    private fun hmacOrNull(data: String): ByteArray? =
        try {
            val secretBytes = Base64.getDecoder().decode(secretBase64)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
            mac.doFinal(data.toByteArray(Charsets.UTF_8))
        } catch (ex: IllegalArgumentException) {
            null
        }

    private fun base64UrlDecodeOrNull(value: String): ByteArray? =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (ex: IllegalArgumentException) {
            null
        }
}
