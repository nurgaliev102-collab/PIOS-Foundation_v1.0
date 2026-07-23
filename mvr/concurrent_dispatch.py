import hashlib
import json
import sqlite3

from transactional_dispatch import TransactionalDispatchService
from stateful_dispatch import Assignment, DispatchConflict, ProposalStatus


class ConcurrentDispatchService(TransactionalDispatchService):
    """Multi-instance-safe ACCEPT boundary with one atomic DB commit.

    assignment_guard and ProposalCommitted are written in the SAME SQLite
    transaction. A process crash therefore cannot expose ownership without
    durable audit evidence, or audit evidence without ownership.
    """

    def _ensure_concurrency_schema(self) -> None:
        self.ledger.conn.executescript("""
        CREATE TABLE IF NOT EXISTS assignment_guard (
            order_id INTEGER PRIMARY KEY,
            assignment_id TEXT NOT NULL UNIQUE,
            proposal_id TEXT NOT NULL UNIQUE,
            driver_id INTEGER NOT NULL,
            idempotency_key TEXT NOT NULL
        );
        """)
        self.ledger.conn.commit()

    def __init__(self, db_path: str):
        super().__init__(db_path)
        self._ensure_concurrency_schema()
        self._recover_assignment_guards()
        self.failpoint = None

    def _recover_assignment_guards(self) -> None:
        rows = self.ledger.conn.execute("SELECT * FROM assignment_guard ORDER BY order_id").fetchall()
        for row in rows:
            proposal = self.core.proposals.get(row["proposal_id"])
            if proposal is None:
                raise RuntimeError("ASSIGNMENT_GUARD_WITHOUT_PROPOSAL")
            assignment = Assignment(row["assignment_id"], row["order_id"], row["driver_id"], row["proposal_id"])
            existing = self.core.assignment_by_order.get(row["order_id"])
            if existing is not None and existing != assignment:
                raise RuntimeError("LEDGER_GUARD_DIVERGENCE")
            self.core.assignment_by_order[row["order_id"]] = assignment
            proposal.status = ProposalStatus.COMMITTED
            self.core._assignment_seq = max(self.core._assignment_seq, int(assignment.id[1:]))
        self.core.assert_invariants()

    @staticmethod
    def _event_hash(prev_hash, event_id, event_type, aggregate_id, payload_json):
        raw = "|".join((prev_hash, event_id, event_type, aggregate_id, payload_json))
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def accept(self, proposal_id: str, idempotency_key: str) -> Assignment:
        self._recover()
        self._ensure_concurrency_schema()
        self._recover_assignment_guards()
        proposal = self.core.proposals[proposal_id]
        conn = self.ledger.conn

        try:
            conn.execute("BEGIN IMMEDIATE")
            existing = conn.execute("SELECT * FROM assignment_guard WHERE order_id = ?", (proposal.order_id,)).fetchone()
            if existing:
                if existing["proposal_id"] == proposal_id:
                    conn.commit()
                    return Assignment(existing["assignment_id"], existing["order_id"], existing["driver_id"], existing["proposal_id"])
                raise DispatchConflict("ORDER_ALREADY_ASSIGNED")

            if proposal.status != ProposalStatus.OPEN:
                raise DispatchConflict("PROPOSAL_NOT_OPEN")

            max_row = conn.execute("SELECT assignment_id FROM assignment_guard ORDER BY CAST(SUBSTR(assignment_id,2) AS INTEGER) DESC LIMIT 1").fetchone()
            next_seq = int(max_row["assignment_id"][1:]) + 1 if max_row else 1
            assignment_id = f"A{next_seq}"
            conn.execute(
                "INSERT INTO assignment_guard(order_id,assignment_id,proposal_id,driver_id,idempotency_key) VALUES(?,?,?,?,?)",
                (proposal.order_id, assignment_id, proposal_id, proposal.driver_id, idempotency_key),
            )

            if self.failpoint == "after_guard_before_event":
                raise RuntimeError("INJECTED_CRASH_AFTER_GUARD")

            event_id = f"proposal-committed:{proposal_id}"
            payload = {"proposal_id": proposal_id, "assignment_id": assignment_id,
                       "order_id": proposal.order_id, "driver_id": proposal.driver_id,
                       "idempotency_key": idempotency_key}
            payload_json = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            last = conn.execute("SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1").fetchone()
            prev_hash = last["event_hash"] if last else "GENESIS"
            event_hash = self._event_hash(prev_hash, event_id, "ProposalCommitted", str(proposal.order_id), payload_json)
            conn.execute(
                "INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)",
                (event_id, "ProposalCommitted", str(proposal.order_id), payload_json, prev_hash, event_hash),
            )

            if self.failpoint == "after_event_before_commit":
                raise RuntimeError("INJECTED_CRASH_AFTER_EVENT")

            conn.commit()
        except Exception:
            if conn.in_transaction:
                conn.rollback()
            raise

        self._recover()
        self._recover_assignment_guards()
        return self.core.assignment_by_order[proposal.order_id]
