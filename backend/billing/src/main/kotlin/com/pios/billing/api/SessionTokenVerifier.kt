package com.pios.billing.api

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
 * specifies. Replicated identically from
 * `com.pios.drivermanagement.api.SessionTokenVerifier`, package line only
 * changed — the same shape and justification `OwnerCredentialGate`
 * already established for its own multi-module replication
 * (`MODULE_STRUCTURE.md` Section 4 forbids shared business code between
 * modules). Billing is the sixth copy, added by ADR-074 Part 4's one
 * authorized read seam: `GET /v1/subscriptions/{driverId}`, session-token-
 * gated to that same driver (the `drv`-claim check already replicated in
 * five other modules per ADR-055 Decision 1).
 *
 * `pios.session.secret` (base64) must be identical, byte for byte, across
 * every module that verifies — issuing and verifying share one secret
 * (ADR-055's own disclosed Consequence). An unset secret makes every
 * verification return `null` — closed by default.
 */
@Component
class SessionTokenVerifier(
    @Value("\${pios.session.secret:}") private val secretBase64: String
) {
    private val objectMapper = ObjectMapper()

    data class VerifiedToken(val sub: String, val drv: String?)

    /**
     * Verifies [authorizationHeader] (the raw `Authorization` header
     * value, or `null` if absent) as a `Bearer` session token. Returns
     * `null` for anything not a currently-valid, correctly-signed,
     * unexpired token — missing header, wrong scheme, malformed token,
     * bad signature, and an expired token all collapse to the same
     * `null`.
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
        return VerifiedToken(sub, drv)
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
