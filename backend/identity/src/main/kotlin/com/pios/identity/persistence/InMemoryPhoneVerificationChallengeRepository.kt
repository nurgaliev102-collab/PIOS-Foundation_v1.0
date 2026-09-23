package com.pios.identity.persistence

import com.pios.identity.application.PhoneVerificationChallengeRepository
import com.pios.identity.domain.IdentityId
import com.pios.identity.domain.PhoneVerificationChallenge
import com.pios.identity.domain.PhoneVerificationPurpose
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A minimal in-memory adapter for [PhoneVerificationChallengeRepository] —
 * a fast, dependency-free test double, mirroring [InMemoryIdentityRepository]'s
 * own role exactly. Not a production storage mechanism, and not a
 * concurrency proof: [findLiveForUpdate] takes a coarse, whole-repository
 * lock rather than a real per-row database lock, which is sufficient to
 * keep unit tests deterministic but is not what proves ADR-082's
 * concurrent-verification invariant — that proof requires the real
 * PostgreSQL adapter and a real two-thread race (see
 * `PhoneVerificationChallengePostgreSQLTest`).
 */
class InMemoryPhoneVerificationChallengeRepository : PhoneVerificationChallengeRepository {
    private val store = ConcurrentHashMap<String, PhoneVerificationChallenge>()
    private val lock = Any()

    override fun save(challenge: PhoneVerificationChallenge) {
        store[challenge.id] = challenge
    }

    override fun supersedeLive(identityId: IdentityId, purpose: PhoneVerificationPurpose, at: Instant) {
        synchronized(lock) {
            store.values
                .filter { it.identityId == identityId && it.purpose == purpose && it.isLive }
                .forEach { store[it.id] = it.copy(supersededAt = at) }
        }
    }

    override fun findLiveForUpdate(identityId: IdentityId, purpose: PhoneVerificationPurpose): PhoneVerificationChallenge? =
        synchronized(lock) {
            store.values.firstOrNull { it.identityId == identityId && it.purpose == purpose && it.isLive }
        }

    override fun update(challenge: PhoneVerificationChallenge) {
        synchronized(lock) {
            store[challenge.id] = challenge
        }
    }

    fun findById(id: String): PhoneVerificationChallenge? = store[id]

    fun nullCiphertext(id: String) {
        synchronized(lock) {
            store[id]?.let { store[id] = it.copy(otpCiphertext = null, otpNonce = null) }
        }
    }

    fun supersededChallengeIds(identityId: String, purpose: String): Set<String> = synchronized(lock) {
        store.values
            .filter { it.identityId.value == identityId && it.purpose.name == purpose && it.supersededAt != null }
            .mapTo(mutableSetOf()) { it.id }
    }
}
