import hashlib
import json
from dataclasses import dataclass
from typing import List

from concurrent_dispatch import ConcurrentDispatchService
from stateful_dispatch import Assignment, DispatchConflict, Proposal, ProposalStatus


@dataclass(frozen=True)
class FencedProposal:
    proposal: Proposal
    fence_token: int


class FencedDispatchService(ConcurrentDispatchService):
    """Persistent temporal fencing with atomic proposal-generation creation."""

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
        self.fence_failpoint = None

    def propose_fenced(self, order_id: int, driver_id: int, eligible_driver_ids: List[int], reason: str = "fairness") -> FencedProposal:
        self._recover()
        self._recover_assignment_guards()
        conn = self.ledger.conn
        conn.execute("BEGIN IMMEDIATE")
        try:
            if conn.execute("SELECT 1 FROM assignment_guard WHERE order_id=?", (order_id,)).fetchone():
                raise DispatchConflict("ORDER_ALREADY_ASSIGNED")

            open_row = conn.execute(
                "SELECT pf.proposal_id FROM proposal_fence pf "
                "JOIN order_fence of ON of.order_id=pf.order_id AND of.current_token=pf.fence_token "
                "WHERE pf.order_id=? ORDER BY pf.fence_token DESC LIMIT 1", (order_id,)
            ).fetchone()
            if open_row:
                existing = self.core.proposals.get(open_row["proposal_id"])
                if existing and existing.status == ProposalStatus.OPEN:
                    raise DispatchConflict("OPEN_PROPOSAL_EXISTS")

            row = conn.execute("SELECT current_token FROM order_fence WHERE order_id=?", (order_id,)).fetchone()
            token = (row["current_token"] + 1) if row else 1
            proposal_id = f"P{self.core._proposal_seq + 1}"
            decision_id = f"DR-{order_id}-G{token}"

            conn.execute(
                "INSERT INTO order_fence(order_id,current_token) VALUES(?,?) "
                "ON CONFLICT(order_id) DO UPDATE SET current_token=excluded.current_token", (order_id, token)
            )
            conn.execute(
                "INSERT INTO proposal_fence(proposal_id,order_id,fence_token) VALUES(?,?,?)",
                (proposal_id, order_id, token),
            )
            eligible_json = json.dumps({"ids": [str(x) for x in eligible_driver_ids]}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            decision_payload = json.dumps({"fence_token": token, "order_id": order_id, "selected_driver_id": driver_id}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            conn.execute(
                "INSERT INTO decision_record(decision_id,order_id,selected_driver_id,eligible_driver_ids_json,reason,payload_json) VALUES(?,?,?,?,?,?)",
                (decision_id, str(order_id), str(driver_id), eligible_json, reason, decision_payload),
            )

            if self.fence_failpoint == "after_fence_before_event":
                raise RuntimeError("INJECTED_CRASH_AFTER_FENCE")

            payload = {"proposal_id": proposal_id, "order_id": order_id, "driver_id": driver_id, "fence_token": token}
            payload_json = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            last = conn.execute("SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1").fetchone()
            prev_hash = last["event_hash"] if last else "GENESIS"
            event_id = f"proposal-offered:{proposal_id}"
            raw = "|".join((prev_hash, event_id, "ProposalOffered", str(order_id), payload_json))
            event_hash = hashlib.sha256(raw.encode("utf-8")).hexdigest()
            conn.execute(
                "INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)",
                (event_id, "ProposalOffered", str(order_id), payload_json, prev_hash, event_hash),
            )

            if self.fence_failpoint == "after_event_before_commit":
                raise RuntimeError("INJECTED_CRASH_AFTER_PROPOSAL_EVENT")
            conn.commit()
        except Exception:
            if conn.in_transaction:
                conn.rollback()
            raise

        self._recover()
        proposal = self.core.proposals[proposal_id]
        return FencedProposal(proposal, token)

    def _assert_current_fence(self, proposal_id: str, fence_token: int) -> Proposal:
        row = self.ledger.conn.execute(
            "SELECT pf.order_id,pf.fence_token,of.current_token FROM proposal_fence pf "
            "JOIN order_fence of ON of.order_id=pf.order_id WHERE pf.proposal_id=?", (proposal_id,)
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
