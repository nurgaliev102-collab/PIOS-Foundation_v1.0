package com.pios.passengerexperience.application

import com.pios.passengerexperience.domain.ConnectionId

/**
 * Thrown when an application-layer operation needs the Connection
 * identified by [connectionId], but [ConnectionRepository.findById] could
 * not locate one. Mirrors dispatch's own
 * `com.pios.dispatch.application.ProposalNotFoundException` convention
 * (see that class's own KDoc) -- an application-layer error, not a domain
 * error (`Connection` itself has no notion of "not found"), and not a
 * persistence error (the repository itself never throws for a miss);
 * this exception is raised exactly once, here, at the point the
 * application decides it cannot proceed without the aggregate.
 */
class ConnectionNotFoundException(val connectionId: ConnectionId) :
    RuntimeException("Connection ${connectionId.value} was not found")
