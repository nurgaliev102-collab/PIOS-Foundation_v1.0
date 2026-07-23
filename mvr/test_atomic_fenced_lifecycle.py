import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService


class AtomicFencedLifecycleTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self.s = FencedDispatchService(self.path)

    def tearDown(self):
        try: self.s.close()
        except Exception: pass
        for suffix in ("", "-wal", "-shm"):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def test_crash_after_fence_rolls_back_generation_decision_and_event(self):
        self.s.fence_failpoint = "after_fence_before_event"
        with self.assertRaisesRegex(RuntimeError, "INJECTED_CRASH_AFTER_FENCE"):
            self.s.propose_fenced(1, 10, [10, 11])
        self.assertIsNone(self.s.ledger.conn.execute("SELECT * FROM order_fence WHERE order_id=1").fetchone())
        self.assertEqual(0, self.s.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence").fetchone()[0])
        self.assertEqual(0, self.s.ledger.conn.execute("SELECT COUNT(*) FROM decision_record").fetchone()[0])
        self.assertEqual(0, self.s.ledger.count_events())

    def test_crash_after_event_before_commit_rolls_back_everything(self):
        self.s.fence_failpoint = "after_event_before_commit"
        with self.assertRaisesRegex(RuntimeError, "INJECTED_CRASH_AFTER_PROPOSAL_EVENT"):
            self.s.propose_fenced(1, 10, [10])
        self.assertEqual(0, self.s.ledger.conn.execute("SELECT COUNT(*) FROM order_fence").fetchone()[0])
        self.assertEqual(0, self.s.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence").fetchone()[0])
        self.assertEqual(0, self.s.ledger.conn.execute("SELECT COUNT(*) FROM decision_record").fetchone()[0])
        self.assertEqual(0, self.s.ledger.count_events())
        self.assertTrue(self.s.ledger.verify_chain())

    def test_retry_after_crash_uses_first_generation_without_gap(self):
        self.s.fence_failpoint = "after_fence_before_event"
        with self.assertRaises(RuntimeError):
            self.s.propose_fenced(1, 10, [10])
        self.s.fence_failpoint = None
        fp = self.s.propose_fenced(1, 10, [10])
        self.assertEqual(1, fp.fence_token)
        self.assertEqual("P1", fp.proposal.id)

    def test_success_persists_generation_decision_fence_and_event_together(self):
        fp = self.s.propose_fenced(1, 10, [10, 11])
        self.assertEqual(1, fp.fence_token)
        self.assertEqual(1, self.s.ledger.conn.execute("SELECT COUNT(*) FROM order_fence").fetchone()[0])
        self.assertEqual(1, self.s.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence").fetchone()[0])
        self.assertEqual(1, self.s.ledger.conn.execute("SELECT COUNT(*) FROM decision_record").fetchone()[0])
        events = self.s.ledger.replay()
        self.assertEqual(1, len(events))
        self.assertEqual("ProposalOffered", events[0].event_type)
        self.assertEqual(1, events[0].payload["fence_token"])
        self.assertTrue(self.s.ledger.verify_chain())


if __name__ == "__main__":
    unittest.main()
