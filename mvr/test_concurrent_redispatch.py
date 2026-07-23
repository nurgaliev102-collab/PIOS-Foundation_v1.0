import os
import tempfile
import threading
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class ConcurrentRedispatchTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self.a = FencedDispatchService(self.path)
        self.b = FencedDispatchService(self.path)

    def tearDown(self):
        for s in (self.a, self.b):
            try: s.close()
            except Exception: pass
        for suffix in ("", "-wal", "-shm"):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def test_two_instances_cannot_create_two_open_generations(self):
        barrier = threading.Barrier(2)
        results, errors = [], []
        def worker(service, driver):
            try:
                barrier.wait()
                results.append(service.propose_fenced(1, driver, [10, 11]))
            except Exception as exc:
                errors.append(exc)
        t1 = threading.Thread(target=worker, args=(self.a, 10))
        t2 = threading.Thread(target=worker, args=(self.b, 11))
        t1.start(); t2.start(); t1.join(); t2.join()
        self.assertEqual(1, len(results))
        self.assertEqual(1, len(errors))
        self.assertIsInstance(errors[0], DispatchConflict)
        self.assertEqual("OPEN_PROPOSAL_EXISTS", str(errors[0]))
        self.assertEqual(1, self.a.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence WHERE order_id=1").fetchone()[0])
        self.assertEqual(1, self.a.ledger.conn.execute("SELECT current_token FROM order_fence WHERE order_id=1").fetchone()[0])

    def test_concurrent_orders_get_distinct_durable_proposal_ids(self):
        barrier = threading.Barrier(2)
        results, errors = [], []
        def worker(service, order_id, driver):
            try:
                barrier.wait()
                results.append(service.propose_fenced(order_id, driver, [driver]))
            except Exception as exc:
                errors.append(exc)
        t1 = threading.Thread(target=worker, args=(self.a, 1, 10))
        t2 = threading.Thread(target=worker, args=(self.b, 2, 11))
        t1.start(); t2.start(); t1.join(); t2.join()
        self.assertEqual([], errors)
        self.assertEqual({"P1", "P2"}, {r.proposal.id for r in results})
        self.assertEqual(2, self.a.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence").fetchone()[0])
        self.assertTrue(self.a.ledger.verify_chain())

    def test_redispatch_after_lapse_allocates_exactly_next_generation(self):
        first = self.a.propose_fenced(1, 10, [10, 11])
        self.a.lapse_fenced(first.proposal.id, first.fence_token)
        # Both instances refresh from durable state; only one may win generation 2.
        self.b._recover()
        barrier = threading.Barrier(2)
        results, errors = [], []
        def worker(service, driver):
            try:
                barrier.wait()
                results.append(service.propose_fenced(1, driver, [10, 11]))
            except Exception as exc:
                errors.append(exc)
        t1 = threading.Thread(target=worker, args=(self.a, 10))
        t2 = threading.Thread(target=worker, args=(self.b, 11))
        t1.start(); t2.start(); t1.join(); t2.join()
        self.assertEqual(1, len(results), f"concurrent redispatch errors={errors!r}")
        self.assertEqual(2, results[0].fence_token)
        self.assertEqual(1, len(errors))
        self.assertEqual("OPEN_PROPOSAL_EXISTS", str(errors[0]))
        self.assertEqual(2, self.a.ledger.conn.execute("SELECT current_token FROM order_fence WHERE order_id=1").fetchone()[0])
        self.assertEqual(2, self.a.ledger.conn.execute("SELECT COUNT(*) FROM proposal_fence WHERE order_id=1").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
