import os
import sqlite3
import tempfile
import unittest

from persistent_ledger import PersistentEventLedger


class PersistentLedgerTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self.ledger = PersistentEventLedger(self.path)

    def tearDown(self):
        try:
            self.ledger.close()
        except Exception:
            pass
        for suffix in ("", "-wal", "-shm"):
            try:
                os.remove(self.path + suffix)
            except FileNotFoundError:
                pass

    def test_append_survives_restart_and_replays_in_order(self):
        self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D1"})
        self.ledger.append("E2", "ProposalCommitted", "O1", {"driver_id": "D1", "assignment_id": "A1"})
        self.ledger.close()
        self.ledger = PersistentEventLedger(self.path)
        events = self.ledger.replay()
        self.assertEqual(["E1", "E2"], [e.event_id for e in events])
        self.assertTrue(self.ledger.verify_chain())

    def test_retry_same_event_is_idempotent(self):
        first = self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D1"})
        second = self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D1"})
        self.assertEqual(first, second)
        self.assertEqual(1, self.ledger.count_events())

    def test_retry_same_id_with_different_payload_is_rejected(self):
        self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D1"})
        with self.assertRaisesRegex(ValueError, "IDEMPOTENCY_CONFLICT"):
            self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D2"})

    def test_hash_chain_detects_tampering(self):
        self.ledger.append("E1", "ProposalOffered", "O1", {"driver_id": "D1"})
        self.ledger.append("E2", "ProposalDeclined", "O1", {"driver_id": "D1"})
        self.assertTrue(self.ledger.verify_chain())
        self.ledger.conn.execute("UPDATE event_ledger SET payload_json = ? WHERE event_id = 'E1'", ('{"driver_id":"ATTACKER"}',))
        self.ledger.conn.commit()
        self.assertFalse(self.ledger.verify_chain())

    def test_decision_record_is_immutable_and_idempotent(self):
        args = ("DR1", "O1", "D1", ["D1", "D2"], "fairness", {"nit": 120})
        self.ledger.record_decision(*args)
        self.ledger.record_decision(*args)
        count = self.ledger.conn.execute("SELECT COUNT(*) FROM decision_record").fetchone()[0]
        self.assertEqual(1, count)
        with self.assertRaisesRegex(ValueError, "IDEMPOTENCY_CONFLICT"):
            self.ledger.record_decision("DR1", "O1", "D2", ["D1", "D2"], "override", {})

    def test_failed_transaction_leaves_no_partial_event(self):
        self.ledger.append("E1", "ProposalOffered", "O1", {})
        before = self.ledger.count_events()
        # Force a UNIQUE hash collision inside the transaction; INSERT must roll back entirely.
        original_hash = self.ledger._hash
        existing_hash = self.ledger.replay()[0].event_hash
        self.ledger._hash = lambda *args: existing_hash
        try:
            with self.assertRaises(sqlite3.IntegrityError):
                self.ledger.append("E2", "ProposalOffered", "O2", {})
        finally:
            self.ledger._hash = original_hash
        self.assertEqual(before, self.ledger.count_events())
        self.assertIsNone(self.ledger.conn.execute("SELECT 1 FROM event_ledger WHERE event_id='E2'").fetchone())
        self.assertTrue(self.ledger.verify_chain())


if __name__ == "__main__":
    unittest.main()
