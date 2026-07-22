import os
from typing import Dict, List, Optional

from persistent_ledger import PersistentEventLedger
from stateful_dispatch import Assignment, DispatchConflict, Proposal, ProposalStatus, StatefulDispatchCore


class TransactionalDispatchService:
    """Crash-recoverable facade joining lifecycle state to durable evidence.

    The ledger is the recovery source of truth. Every externally observable
    transition is persisted before the method returns. Recovery deterministically
    rebuilds in-memory state from ordered events.
    """

    def __init__(self, db_path: str):
        self.db_path = db_path
        self.ledger = PersistentEventLedger(db_path)
        self.core = StatefulDispatchCore()
        self._recover()

    def _recover(self) -> None:
        self.core = StatefulDispatchCore()
        for event in self.ledger.replay():
            p = event.payload
            if event.event_type == "ProposalOffered":
                proposal = Proposal(p["proposal_id"], int(p["order_id"]), int(p["driver_id"]), ProposalStatus.OPEN)
                self.core.proposals[proposal.id] = proposal
                self.core.proposal_ids_by_order.setdefault(proposal.order_id, []).append(proposal.id)
                self.core._proposal_seq = max(self.core._proposal_seq, int(proposal.id[1:]))
            elif event.event_type == "ProposalDeclined":
                self.core.proposals[p["proposal_id"]].status = ProposalStatus.DECLINED
            elif event.event_type == "ProposalLapsed":
                self.core.proposals[p["proposal_id"]].status = ProposalStatus.LAPSED
            elif event.event_type == "ProposalCommitted":
                proposal = self.core.proposals[p["proposal_id"]]
                assignment = Assignment(p["assignment_id"], proposal.order_id, proposal.driver_id, proposal.id)
                self.core.assignment_by_order[proposal.order_id] = assignment
                proposal.status = ProposalStatus.COMMITTED
                self.core._assignment_seq = max(self.core._assignment_seq, int(assignment.id[1:]))
        self.core.assert_invariants()

    def propose(self, order_id: int, driver_id: int, eligible_driver_ids: List[int], reason: str = "fairness") -> Proposal:
        # Decision evidence is durable before ProposalOffered becomes durable/observable.
        decision_id = f"DR-{order_id}-{driver_id}"
        self.ledger.record_decision(
            decision_id, str(order_id), str(driver_id), [str(x) for x in eligible_driver_ids], reason,
            {"order_id": order_id, "selected_driver_id": driver_id},
        )
        proposal = self.core.propose(order_id, driver_id, eligible_driver_ids, reason)
        try:
            self.ledger.append(
                f"proposal-offered:{proposal.id}", "ProposalOffered", str(order_id),
                {"proposal_id": proposal.id, "order_id": order_id, "driver_id": driver_id},
            )
        except Exception:
            # Never leave an in-memory observable proposal that lacks durable evidence.
            self.core = StatefulDispatchCore()
            self._recover()
            raise
        return proposal

    def decline(self, proposal_id: str) -> None:
        proposal = self.core.proposals[proposal_id]
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        self.ledger.append(
            f"proposal-declined:{proposal_id}", "ProposalDeclined", str(proposal.order_id),
            {"proposal_id": proposal_id},
        )
        proposal.status = ProposalStatus.DECLINED

    def lapse(self, proposal_id: str) -> None:
        proposal = self.core.proposals[proposal_id]
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        self.ledger.append(
            f"proposal-lapsed:{proposal_id}", "ProposalLapsed", str(proposal.order_id),
            {"proposal_id": proposal_id},
        )
        proposal.status = ProposalStatus.LAPSED

    def accept(self, proposal_id: str, idempotency_key: str) -> Assignment:
        proposal = self.core.proposals[proposal_id]
        existing = self.core.assignment_by_order.get(proposal.order_id)
        if existing:
            if existing.proposal_id == proposal_id:
                return existing
            raise DispatchConflict("ORDER_ALREADY_ASSIGNED")
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")

        next_assignment_id = f"A{self.core._assignment_seq + 1}"
        # Durable commit event contains enough data to reconstruct Assignment atomically on replay.
        self.ledger.append(
            f"proposal-committed:{proposal_id}", "ProposalCommitted", str(proposal.order_id),
            {"proposal_id": proposal_id, "assignment_id": next_assignment_id,
             "order_id": proposal.order_id, "driver_id": proposal.driver_id,
             "idempotency_key": idempotency_key},
        )
        assignment = Assignment(next_assignment_id, proposal.order_id, proposal.driver_id, proposal.id)
        self.core._assignment_seq += 1
        self.core.assignment_by_order[proposal.order_id] = assignment
        proposal.status = ProposalStatus.COMMITTED
        self.core.idempotency_results[(proposal_id, idempotency_key)] = assignment
        self.core.assert_invariants()
        return assignment

    def close(self) -> None:
        self.ledger.close()
