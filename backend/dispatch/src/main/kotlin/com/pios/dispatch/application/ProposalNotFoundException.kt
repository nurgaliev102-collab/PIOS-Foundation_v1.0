package com.pios.dispatch.application

import com.pios.dispatch.domain.ProposalId

/**
 * Thrown when an application-layer operation needs the Proposal
 * identified by [proposalId], but [ProposalRepository.findById] could not
 * locate one. Mirrors [AssignmentNotFoundException] exactly: an
 * application-layer error, not a domain error (the Proposal aggregate
 * itself has no notion of "not found") and not a persistence error (the
 * repository itself never throws for a miss; this exception is raised
 * exactly once, here, at the point the application decides it cannot
 * proceed without the aggregate).
 */
class ProposalNotFoundException(val proposalId: ProposalId) :
    RuntimeException("Proposal ${proposalId.value} was not found")
