import hashlib
import json
import sqlite3
from dataclasses import dataclass
from typing import Any, Dict, List, Optional


@dataclass(frozen=True)
class LedgerEvent:
    seq: int
    event_id: str
    event_type: str
    aggregate_id: str
    payload: Dict[str, Any]
    prev_hash: str
    event_hash: str


class PersistentEventLedger:
    """SQLite-backed append-only ledger with idempotent event writes and hash chaining."""

    def __init__(self, db_path: str):
        self.db_path = db_path
        # Service instances may be constructed on an application/control thread
        # and execute a request on a worker thread. SQLite's default thread-affinity
        # check would reject that valid ownership transfer before DB serialization
        # can even be exercised. Each service still owns one connection; concurrent
        # assignment ownership is serialized by BEGIN IMMEDIATE + DB constraints.
        self.conn = sqlite3.connect(db_path, check_same_thread=False, timeout=30.0)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA synchronous=FULL")
        self.conn.executescript("""
        CREATE TABLE IF NOT EXISTS event_ledger (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            event_id TEXT NOT NULL UNIQUE,
            event_type TEXT NOT NULL,
            aggregate_id TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            prev_hash TEXT NOT NULL,
            event_hash TEXT NOT NULL UNIQUE
        );
        CREATE TABLE IF NOT EXISTS decision_record (
            decision_id TEXT PRIMARY KEY,
            order_id TEXT NOT NULL,
            selected_driver_id TEXT NOT NULL,
            eligible_driver_ids_json TEXT NOT NULL,
            reason TEXT NOT NULL,
            payload_json TEXT NOT NULL
        );
        """)
        self.conn.commit()

    @staticmethod
    def _canonical(value: Dict[str, Any]) -> str:
        return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)

    @staticmethod
    def _hash(prev_hash: str, event_id: str, event_type: str, aggregate_id: str, payload_json: str) -> str:
        raw = "|".join((prev_hash, event_id, event_type, aggregate_id, payload_json))
        return hashlib.sha256(raw.encode("utf-8")).hexdigest()

    def append(self, event_id: str, event_type: str, aggregate_id: str, payload: Dict[str, Any]) -> LedgerEvent:
        existing = self.conn.execute("SELECT * FROM event_ledger WHERE event_id = ?", (event_id,)).fetchone()
        if existing:
            candidate_payload = self._canonical(payload)
            if (existing["event_type"] != event_type or existing["aggregate_id"] != aggregate_id or
                    existing["payload_json"] != candidate_payload):
                raise ValueError("IDEMPOTENCY_CONFLICT")
            return self._row_to_event(existing)

        payload_json = self._canonical(payload)
        with self.conn:
            last = self.conn.execute("SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1").fetchone()
            prev_hash = last["event_hash"] if last else "GENESIS"
            event_hash = self._hash(prev_hash, event_id, event_type, aggregate_id, payload_json)
            self.conn.execute(
                "INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)",
                (event_id, event_type, aggregate_id, payload_json, prev_hash, event_hash),
            )
        row = self.conn.execute("SELECT * FROM event_ledger WHERE event_id = ?", (event_id,)).fetchone()
        return self._row_to_event(row)

    def record_decision(self, decision_id: str, order_id: str, selected_driver_id: str,
                        eligible_driver_ids: List[str], reason: str, payload: Optional[Dict[str, Any]] = None) -> None:
        body = payload or {}
        values = (decision_id, order_id, selected_driver_id, self._canonical({"ids": eligible_driver_ids}), reason, self._canonical(body))
        existing = self.conn.execute("SELECT * FROM decision_record WHERE decision_id = ?", (decision_id,)).fetchone()
        if existing:
            current = tuple(existing[k] for k in ("decision_id", "order_id", "selected_driver_id", "eligible_driver_ids_json", "reason", "payload_json"))
            if current != values:
                raise ValueError("IDEMPOTENCY_CONFLICT")
            return
        with self.conn:
            self.conn.execute(
                "INSERT INTO decision_record(decision_id,order_id,selected_driver_id,eligible_driver_ids_json,reason,payload_json) VALUES(?,?,?,?,?,?)",
                values,
            )

    def replay(self) -> List[LedgerEvent]:
        return [self._row_to_event(row) for row in self.conn.execute("SELECT * FROM event_ledger ORDER BY seq")]

    def verify_chain(self) -> bool:
        expected_prev = "GENESIS"
        for row in self.conn.execute("SELECT * FROM event_ledger ORDER BY seq"):
            expected_hash = self._hash(expected_prev, row["event_id"], row["event_type"], row["aggregate_id"], row["payload_json"])
            if row["prev_hash"] != expected_prev or row["event_hash"] != expected_hash:
                return False
            expected_prev = row["event_hash"]
        return True

    def count_events(self) -> int:
        return self.conn.execute("SELECT COUNT(*) FROM event_ledger").fetchone()[0]

    @staticmethod
    def _row_to_event(row: sqlite3.Row) -> LedgerEvent:
        return LedgerEvent(row["seq"], row["event_id"], row["event_type"], row["aggregate_id"], json.loads(row["payload_json"]), row["prev_hash"], row["event_hash"])

    def close(self) -> None:
        self.conn.close()
