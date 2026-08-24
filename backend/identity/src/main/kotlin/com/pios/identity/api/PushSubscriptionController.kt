package com.pios.identity.api

import com.pios.identity.persistence.PostgreSQLPushSubscriptionRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Authenticated browser push subscription boundary. Ownership always comes from the verified token subject. */
@RestController
@RequestMapping("/v1/identities/push-subscriptions")
class PushSubscriptionController(
    private val repository: PostgreSQLPushSubscriptionRepository,
    private val sessionTokenVerifier: SessionTokenVerifier,
) {
    @PostMapping
    fun register(
        @RequestBody request: RegisterPushSubscriptionRequest,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Unit> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(401).build()
        if (request.endpoint.isBlank() || request.p256dh.isBlank() || request.auth.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        repository.upsert(verified.sub, request.endpoint, request.p256dh, request.auth, request.userAgent)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun delete(
        @RequestBody request: DeletePushSubscriptionRequest,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): ResponseEntity<Unit> {
        val verified = sessionTokenVerifier.verify(authorization)
            ?: return ResponseEntity.status(401).build()
        if (request.endpoint.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        repository.delete(verified.sub, request.endpoint)
        return ResponseEntity.noContent().build()
    }
}

data class RegisterPushSubscriptionRequest(
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val userAgent: String?,
)

data class DeletePushSubscriptionRequest(
    val endpoint: String,
)
