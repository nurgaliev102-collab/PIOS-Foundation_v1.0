from dataclasses import dataclass
from enum import Enum
from typing import Dict, List, Optional, Tuple


class ProposalStatus(str, Enum):
    OPEN = "OPEN"
    DECLINED = "DECLINED"
    LAPSED = "LAPSED"
    COMMITTED = "COMMITTED"


@dataclass
class Proposal:
    id: str
    order_id: int
    driver_id: int
    status: ProposalStatus = ProposalStatus.OPEN


@dataclass(frozen=True)
class Assignment:
    id: str
    order_id: int
    driver_id: int
    proposal_id: str


class DispatchConflict(Exception):
    pass


class StatefulDispatchCore:
    """Minimal deterministic lifecycle core for Milestone 3.

    Constitution enforced here:
    - at most one OPEN proposal per order;
    - only eligible drivers may receive proposals;
    - a driver is never proposed twice for the same order;
    - ACCEPT is idempotent;
    - one order has at most one assignment;
    - COMMITTED is only observable together with its Assignment.
    """

    def __init__(self):
        self.proposals: Dict[str, Proposal] = {}
        self.assignment_by_order: Dict[int, Assignment] = {}
        self.proposal_ids_by_order: Dict[int, List[str]] = {}
        self.idempotency_results: Dict[Tuple[str, str], Assignment] = {}
        self.events: List[dict] = []
        self.decisions: List[dict] = []
        self._proposal_seq = 0
        self._assignment_seq = 0

    def _open_for_order(self, order_id: int) -> Optional[Proposal]:
        for proposal_id in self.proposal_ids_by_order.get(order_id, []):
            proposal = self.proposals[proposal_id]
            if proposal.status == ProposalStatus.OPEN:
                return proposal
        return None

    def propose(self, order_id: int, driver_id: int, eligible_driver_ids: List[int], reason: str = "fairness") -> Proposal:
        if order_id in self.assignment_by_order:
            raise DispatchConflict("ORDER_ALREADY_ASSIGNED")
        if driver_id not in eligible_driver_ids:
            raise DispatchConflict("DRIVER_NOT_ELIGIBLE")
        if self._open_for_order(order_id) is not None:
            raise DispatchConflict("OPEN_PROPOSAL_EXISTS")
        for proposal_id in self.proposal_ids_by_order.get(order_id, []):
            if self.proposals[proposal_id].driver_id == driver_id:
                raise DispatchConflict("DRIVER_ALREADY_ATTEMPTED")

        self.decisions.append({
            "order_id": order_id,
            "selected_driver_id": driver_id,
            "eligible_driver_ids": list(eligible_driver_ids),
            "reason": reason,
        })
        self._proposal_seq += 1
        proposal = Proposal(f"P{self._proposal_seq}", order_id, driver_id)
        self.proposals[proposal.id] = proposal
        self.proposal_ids_by_order.setdefault(order_id, []).append(proposal.id)
        self.events.append({"type": "ProposalOffered", "proposal_id": proposal.id, "order_id": order_id, "driver_id": driver_id})
        return proposal

    def decline(self, proposal_id: str) -> None:
        proposal = self.proposals[proposal_id]
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        proposal.status = ProposalStatus.DECLINED
        self.events.append({"type": "ProposalDeclined", "proposal_id": proposal.id})

    def lapse(self, proposal_id: str) -> None:
        proposal = self.proposals[proposal_id]
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        proposal.status = ProposalStatus.LAPSED
        self.events.append({"type": "ProposalLapsed", "proposal_id": proposal.id})

    def accept(self, proposal_id: str, idempotency_key: str) -> Assignment:
        key = (proposal_id, idempotency_key)
        if key in self.idempotency_results:
            return self.idempotency_results[key]

        proposal = self.proposals[proposal_id]
        existing = self.assignment_by_order.get(proposal.order_id)
        if existing is not None:
            if existing.proposal_id == proposal_id:
                self.idempotency_results[key] = existing
                return existing
            raise DispatchConflict("ORDER_ALREADY_ASSIGNED")
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")

        # Atomic in-memory commit: construct Assignment first, then expose both states.
        self._assignment_seq += 1
        assignment = Assignment(f"A{self._assignment_seq}", proposal.order_id, proposal.driver_id, proposal.id)
        self.assignment_by_order[proposal.order_id] = assignment
        proposal.status = ProposalStatus.COMMITTED
        self.idempotency_results[key] = assignment
        self.events.append({
            "type": "ProposalCommitted",
            "proposal_id": proposal.id,
            "assignment_id": assignment.id,
            "order_id": proposal.order_id,
            "driver_id": proposal.driver_id,
        })
        return assignment

    def assert_invariants(self) -> None:
        for order_id, proposal_ids in self.proposal_ids_by_order.items():
            open_count = sum(self.proposals[p].status == ProposalStatus.OPEN for p in proposal_ids)
            assert open_count <= 1, f"multiple OPEN proposals for order {order_id}"
            attempted = [self.proposals[p].driver_id for p in proposal_ids]
            assert len(attempted) == len(set(attempted)), f"duplicate driver attempt for order {order_id}"
            committed = [self.proposals[p] for p in proposal_ids if self.proposals[p].status == ProposalStatus.COMMITTED]
            assert len(committed) <= 1, f"multiple COMMITTED proposals for order {order_id}"
            for proposal in committed:
                assignment = self.assignment_by_order.get(order_id)
                assert assignment is not None, f"orphan COMMITTED proposal {proposal.id}"
                assert assignment.proposal_id == proposal.id
                assert assignment.driver_id == proposal.driver_id
