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
    """Persistent fencing with atomic generation and terminal lifecycle transitions."""

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
        CREATE TABLE IF NOT EXISTS proposal_terminal (
            proposal_id TEXT PRIMARY KEY,
            terminal_type TEXT NOT NULL CHECK(terminal_type IN ('COMMITTED','DECLINED','LAPSED'))
        );
        """)
        self.ledger.conn.commit()

    def __init__(self, db_path: str):
        super().__init__(db_path)
        self._ensure_fencing_schema()
        self.fence_failpoint = None
        self.terminal_failpoint = None

    @staticmethod
    def _next_proposal_id(conn) -> str:
        rows = conn.execute("SELECT proposal_id FROM proposal_fence").fetchall()
        max_seq = max((int(row["proposal_id"][1:]) for row in rows), default=0)
        return f"P{max_seq + 1}"

    @staticmethod
    def _durable_proposal_status(conn, proposal_id: str) -> ProposalStatus:
        terminal = conn.execute("SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?", (proposal_id,)).fetchone()
        if terminal:
            return ProposalStatus[terminal["terminal_type"]]
        if conn.execute("SELECT 1 FROM assignment_guard WHERE proposal_id=?", (proposal_id,)).fetchone():
            return ProposalStatus.COMMITTED
        rows = conn.execute(
            "SELECT event_type FROM event_ledger WHERE json_extract(payload_json,'$.proposal_id')=? ORDER BY seq", (proposal_id,)
        ).fetchall()
        status = ProposalStatus.OPEN
        for row in rows:
            if row["event_type"] == "ProposalDeclined": status = ProposalStatus.DECLINED
            elif row["event_type"] == "ProposalLapsed": status = ProposalStatus.LAPSED
            elif row["event_type"] == "ProposalCommitted": status = ProposalStatus.COMMITTED
        return status

    def propose_fenced(self, order_id: int, driver_id: int, eligible_driver_ids: List[int], reason: str = "fairness") -> FencedProposal:
        self._recover(); self._recover_assignment_guards()
        conn = self.ledger.conn
        conn.execute("BEGIN IMMEDIATE")
        try:
            if conn.execute("SELECT 1 FROM assignment_guard WHERE order_id=?", (order_id,)).fetchone():
                raise DispatchConflict("ORDER_ALREADY_ASSIGNED")
            current = conn.execute(
                "SELECT pf.proposal_id FROM proposal_fence pf JOIN order_fence of "
                "ON of.order_id=pf.order_id AND of.current_token=pf.fence_token WHERE pf.order_id=?", (order_id,)
            ).fetchone()
            if current and self._durable_proposal_status(conn, current["proposal_id"]) == ProposalStatus.OPEN:
                raise DispatchConflict("OPEN_PROPOSAL_EXISTS")
            row = conn.execute("SELECT current_token FROM order_fence WHERE order_id=?", (order_id,)).fetchone()
            token = row["current_token"] + 1 if row else 1
            proposal_id = self._next_proposal_id(conn)
            decision_id = f"DR-{order_id}-G{token}"
            conn.execute("INSERT INTO order_fence(order_id,current_token) VALUES(?,?) ON CONFLICT(order_id) DO UPDATE SET current_token=excluded.current_token", (order_id, token))
            conn.execute("INSERT INTO proposal_fence(proposal_id,order_id,fence_token) VALUES(?,?,?)", (proposal_id, order_id, token))
            eligible_json = json.dumps({"ids": [str(x) for x in eligible_driver_ids]}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            decision_payload = json.dumps({"fence_token": token, "order_id": order_id, "selected_driver_id": driver_id}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            conn.execute("INSERT INTO decision_record(decision_id,order_id,selected_driver_id,eligible_driver_ids_json,reason,payload_json) VALUES(?,?,?,?,?,?)", (decision_id, str(order_id), str(driver_id), eligible_json, reason, decision_payload))
            if self.fence_failpoint == "after_fence_before_event": raise RuntimeError("INJECTED_CRASH_AFTER_FENCE")
            self._insert_event(conn, f"proposal-offered:{proposal_id}", "ProposalOffered", str(order_id), {"proposal_id": proposal_id, "order_id": order_id, "driver_id": driver_id, "fence_token": token})
            if self.fence_failpoint == "after_event_before_commit": raise RuntimeError("INJECTED_CRASH_AFTER_PROPOSAL_EVENT")
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        self._recover()
        return FencedProposal(self.core.proposals[proposal_id], token)

    @staticmethod
    def _insert_event(conn, event_id, event_type, aggregate_id, payload):
        payload_json = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
        last = conn.execute("SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1").fetchone()
        prev_hash = last["event_hash"] if last else "GENESIS"
        raw = "|".join((prev_hash, event_id, event_type, aggregate_id, payload_json))
        event_hash = hashlib.sha256(raw.encode("utf-8")).hexdigest()
        conn.execute("INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)", (event_id, event_type, aggregate_id, payload_json, prev_hash, event_hash))

    def _assert_current_fence_row(self, conn, proposal_id, fence_token):
        row = conn.execute("SELECT pf.order_id,pf.fence_token,of.current_token FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id WHERE pf.proposal_id=?", (proposal_id,)).fetchone()
        if row is None: raise DispatchConflict("UNKNOWN_PROPOSAL_FENCE")
        if row["fence_token"] != fence_token or row["current_token"] != fence_token: raise DispatchConflict("STALE_PROPOSAL_FENCE")
        return row

    def _terminal(self, proposal_id: str, fence_token: int, terminal: str) -> None:
        conn = self.ledger.conn
        conn.execute("BEGIN IMMEDIATE")
        try:
            row = self._assert_current_fence_row(conn, proposal_id, fence_token)
            if self._durable_proposal_status(conn, proposal_id) != ProposalStatus.OPEN:
                raise DispatchConflict("PROPOSAL_NOT_OPEN")
            conn.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,?)", (proposal_id, terminal))
            if self.terminal_failpoint == "after_terminal_before_event": raise RuntimeError("INJECTED_CRASH_AFTER_TERMINAL")
            event_type = "ProposalDeclined" if terminal == "DECLINED" else "ProposalLapsed"
            self._insert_event(conn, f"{event_type.lower()}:{proposal_id}", event_type, str(row["order_id"]), {"proposal_id": proposal_id})
            if self.terminal_failpoint == "after_event_before_commit": raise RuntimeError("INJECTED_CRASH_AFTER_TERMINAL_EVENT")
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        self._recover()

    def accept_fenced(self, proposal_id: str, fence_token: int, idempotency_key: str) -> Assignment:
        # Fence check is advisory here; ConcurrentDispatchService.accept owns the
        # assignment transaction. Terminal marker is written only after success.
        conn = self.ledger.conn
        conn.execute("BEGIN IMMEDIATE")
        try:
            self._assert_current_fence_row(conn, proposal_id, fence_token)
            if self._durable_proposal_status(conn, proposal_id) != ProposalStatus.OPEN:
                raise DispatchConflict("PROPOSAL_NOT_OPEN")
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        assignment = super().accept(proposal_id, idempotency_key)
        with conn:
            conn.execute("INSERT OR IGNORE INTO proposal_terminal(proposal_id,terminal_type) VALUES(?, 'COMMITTED')", (proposal_id,))
        return assignment

    def decline_fenced(self, proposal_id: str, fence_token: int) -> None:
        self._terminal(proposal_id, fence_token, "DECLINED")

    def lapse_fenced(self, proposal_id: str, fence_token: int) -> None:
        self._terminal(proposal_id, fence_token, "LAPSED")
