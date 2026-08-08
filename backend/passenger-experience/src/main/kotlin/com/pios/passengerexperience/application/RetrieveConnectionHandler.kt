package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.Connection
import com.pios.passengerexperience.domain.ConnectionId
import org.springframework.stereotype.Service

/**
 * Read-only single-connection lookup (ADR-055 Decision 4) — used by
 * `com.pios.passengerexperience.api.ConnectionController` to check a
 * connection's stored `passengerReference` against the authenticated
 * caller's token *before* deciding whether `POST .../primary` or
 * `DELETE ...` may proceed, without the controller reaching into
 * [ConnectionRepository] directly (that access stays inside the
 * application layer, the same Domain-Owned Persistence discipline every
 * other handler in this module already follows). Returns `null` for an
 * unknown [ConnectionId] rather than throwing — the caller decides what a
 * miss means (ADR-055 Decision 4: a miss and an ownership mismatch are
 * deliberately indistinguishable to the caller on the primary endpoint).
 */
@Service
class RetrieveConnectionHandler(
    private val connectionRepository: ConnectionRepository
) {
    fun handle(connectionId: ConnectionId): Connection? = connectionRepository.findById(connectionId)
}
