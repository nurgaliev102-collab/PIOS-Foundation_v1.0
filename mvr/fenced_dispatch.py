from dataclasses import dataclass
from typing import Dict, List

from concurrent_dispatch import ConcurrentDispatchService
from stateful_dispatch import Assignment, DispatchConflict, Proposal, ProposalStatus


@dataclass(frozen=True)
class FencedProposal:
    proposal: Proposal
    fence_token: int


class FencedDispatchService(ConcurrentDispatchService):
    """Temporal fencing for stale proposal commands.

    Each order owns a monotonically increasing fence token. A command is valid
    only for the proposal generation that is currently authoritative.
    """

    def _ensure_fencing_schema(self) -> None:
        self.ledger.conn.executescript("""
        CREATE TABLE IF NOT EXISTS order_fence (
            order_id INTEGER PRIMARY KEY,
            current_token INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS proposal_fence (
            proposal_id TEXT PRIMARY KEY,
            order_id INTEGER NOT NULL,
            fence_token INTEGER NOT NULL,
            UNIQUE(order_id, fence_token)
        );
        """)
        self.ledger.conn.commit()

    def __init__(self, db_path: str):
        super().__init__(db_path)
        self._ensure_fencing_schema()

    def propose_fenced(self, order_id: int, driver_id: int, eligible_driver_ids: List[int], reason: str = "fairness") -> FencedProposal:
        # Refresh stale process state before generation allocation.
        self._recover()
        self._recover_assignment_guards()
        conn = self.ledger.conn
        conn.execute("BEGIN IMMEDIATE")
        try:
            assigned = conn.execute("SELECT 1 FROM assignment_guard WHERE order_id=?", (order_id,)).fetchone()
            if assigned:
                raise DispatchConflict("ORDER_ALREADY_ASSIGNED")
            row = conn.execute("SELECT current_token FROM order_fence WHERE order_id=?", (order_id,)).fetchone()
            token = (row["current_token"] + 1) if row else 1
            conn.execute(
                "INSERT INTO order_fence(order_id,current_token) VALUES(?,?) "
                "ON CONFLICT(order_id) DO UPDATE SET current_token=excluded.current_token",
                (order_id, token),
            )
            conn.commit()
        except Exception:
            if conn.in_transaction:
                conn.rollback()
            raise

        proposal = super().propose(order_id, driver_id, eligible_driver_ids, reason)
        with conn:
            conn.execute(
                "INSERT INTO proposal_fence(proposal_id,order_id,fence_token) VALUES(?,?,?)",
                (proposal.id, order_id, token),
            )
        return FencedProposal(proposal, token)

    def _assert_current_fence(self, proposal_id: str, fence_token: int) -> Proposal:
        row = self.ledger.conn.execute(
            "SELECT pf.order_id,pf.fence_token,of.current_token "
            "FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id "
            "WHERE pf.proposal_id=?",
            (proposal_id,),
        ).fetchone()
        if row is None:
            raise DispatchConflict("UNKNOWN_PROPOSAL_FENCE")
        if row["fence_token"] != fence_token or row["current_token"] != fence_token:
            raise DispatchConflict("STALE_PROPOSAL_FENCE")
        self._recover()
        self._recover_assignment_guards()
        return self.core.proposals[proposal_id]

    def accept_fenced(self, proposal_id: str, fence_token: int, idempotency_key: str) -> Assignment:
        self._assert_current_fence(proposal_id, fence_token)
        return super().accept(proposal_id, idempotency_key)

    def decline_fenced(self, proposal_id: str, fence_token: int) -> None:
        proposal = self._assert_current_fence(proposal_id, fence_token)
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        super().decline(proposal_id)

    def lapse_fenced(self, proposal_id: str, fence_token: int) -> None:
        proposal = self._assert_current_fence(proposal_id, fence_token)
        if proposal.status != ProposalStatus.OPEN:
            raise DispatchConflict("PROPOSAL_NOT_OPEN")
        super().lapse(proposal_id)
