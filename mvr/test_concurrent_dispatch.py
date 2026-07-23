import os
import tempfile
import threading
import unittest

from concurrent_dispatch import ConcurrentDispatchService
from stateful_dispatch import DispatchConflict


class ConcurrentDispatchTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self.a = ConcurrentDispatchService(self.path)

    def tearDown(self):
        for service in (getattr(self, "a", None), getattr(self, "b", None)):
            if service:
                try: service.close()
                except Exception: pass
        for suffix in ("", "-wal", "-shm"):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def second_instance(self):
        self.b = ConcurrentDispatchService(self.path)
        return self.b

    def test_two_instances_same_accept_produce_one_assignment(self):
        p = self.a.propose(1, 10, [10])
        b = self.second_instance()
        barrier = threading.Barrier(2)
        results, errors = [], []
        def worker(service, key):
            try:
                barrier.wait()
                results.append(service.accept(p.id, key))
            except Exception as exc:
                errors.append(exc)
        t1 = threading.Thread(target=worker, args=(self.a, "K-A"))
        t2 = threading.Thread(target=worker, args=(b, "K-B"))
        t1.start(); t2.start(); t1.join(); t2.join()
        self.assertEqual([], errors)
        self.assertEqual(2, len(results))
        self.assertEqual(results[0], results[1])
        self.assertEqual(1, self.a.ledger.conn.execute("SELECT COUNT(*) FROM assignment_guard WHERE order_id=1").fetchone()[0])
        self.assertEqual(1, len([e for e in self.a.ledger.replay() if e.event_type == "ProposalCommitted"]))

    def test_crash_after_guard_before_event_rolls_back_both(self):
        p = self.a.propose(1, 10, [10])
        self.a.failpoint = "after_guard_before_event"
        with self.assertRaisesRegex(RuntimeError, "INJECTED_CRASH_AFTER_GUARD"):
            self.a.accept(p.id, "K1")
        self.assertEqual(0, self.a.ledger.conn.execute("SELECT COUNT(*) FROM assignment_guard WHERE order_id=1").fetchone()[0])
        self.assertEqual(0, len([e for e in self.a.ledger.replay() if e.event_type == "ProposalCommitted"]))
        self.a.failpoint = None
        assignment = self.a.accept(p.id, "K1-retry")
        self.assertEqual(1, assignment.order_id)

    def test_crash_after_event_before_commit_rolls_back_both(self):
        p = self.a.propose(1, 10, [10])
        self.a.failpoint = "after_event_before_commit"
        with self.assertRaisesRegex(RuntimeError, "INJECTED_CRASH_AFTER_EVENT"):
            self.a.accept(p.id, "K1")
        self.assertEqual(0, self.a.ledger.conn.execute("SELECT COUNT(*) FROM assignment_guard WHERE order_id=1").fetchone()[0])
        self.assertEqual(0, len([e for e in self.a.ledger.replay() if e.event_type == "ProposalCommitted"]))
        self.assertTrue(self.a.ledger.verify_chain())

    def test_stale_instance_cannot_create_second_assignment(self):
        p = self.a.propose(1, 10, [10])
        b = self.second_instance()
        first = self.a.accept(p.id, "K1")
        again = b.accept(p.id, "K2")
        self.assertEqual(first, again)
        self.assertEqual(1, self.a.ledger.conn.execute("SELECT COUNT(*) FROM assignment_guard WHERE order_id=1").fetchone()[0])

    def test_second_proposal_after_assignment_is_rejected_after_refresh(self):
        p = self.a.propose(1, 10, [10, 11])
        b = self.second_instance()
        self.a.accept(p.id, "K1")
        b.close(); self.b = ConcurrentDispatchService(self.path)
        with self.assertRaisesRegex(DispatchConflict, "ORDER_ALREADY_ASSIGNED"):
            self.b.propose(1, 11, [10, 11])

    def test_restart_recovers_single_assignment_guard(self):
        p = self.a.propose(1, 10, [10])
        expected = self.a.accept(p.id, "K1")
        self.a.close()
        self.a = ConcurrentDispatchService(self.path)
        self.assertEqual(expected, self.a.core.assignment_by_order[1])
        self.a.core.assert_invariants()
        self.assertTrue(self.a.ledger.verify_chain())


if __name__ == "__main__":
    unittest.main()
