import os
import tempfile
import unittest

from stateful_dispatch import DispatchConflict, ProposalStatus
from transactional_dispatch import TransactionalDispatchService


class TransactionalDispatchTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self.service = TransactionalDispatchService(self.path)

    def tearDown(self):
        try:
            self.service.close()
        except Exception:
            pass
        for suffix in ("", "-wal", "-shm"):
            try:
                os.remove(self.path + suffix)
            except FileNotFoundError:
                pass

    def restart(self):
        self.service.close()
        self.service = TransactionalDispatchService(self.path)

    def test_decision_is_persisted_before_proposal_event(self):
        p = self.service.propose(1, 10, [10, 11])
        decision = self.service.ledger.conn.execute("SELECT * FROM decision_record").fetchone()
        event = self.service.ledger.replay()[0]
        self.assertIsNotNone(decision)
        self.assertEqual("ProposalOffered", event.event_type)
        self.assertEqual(p.id, event.payload["proposal_id"])

    def test_open_proposal_survives_restart(self):
        p = self.service.propose(1, 10, [10, 11])
        self.restart()
        recovered = self.service.core.proposals[p.id]
        self.assertEqual(ProposalStatus.OPEN, recovered.status)
        with self.assertRaisesRegex(DispatchConflict, "OPEN_PROPOSAL_EXISTS"):
            self.service.propose(1, 11, [10, 11])

    def test_decline_survives_restart_then_next_driver_can_be_proposed(self):
        p1 = self.service.propose(1, 10, [10, 11])
        self.service.decline(p1.id)
        self.restart()
        p2 = self.service.propose(1, 11, [10, 11])
        self.assertEqual(11, p2.driver_id)
        self.service.core.assert_invariants()

    def test_accept_assignment_survives_restart(self):
        p = self.service.propose(1, 10, [10])
        a = self.service.accept(p.id, "K1")
        self.restart()
        recovered = self.service.core.assignment_by_order[1]
        self.assertEqual(a, recovered)
        self.assertEqual(ProposalStatus.COMMITTED, self.service.core.proposals[p.id].status)
        self.service.core.assert_invariants()

    def test_accept_retry_after_restart_is_idempotent(self):
        p = self.service.propose(1, 10, [10])
        first = self.service.accept(p.id, "K1")
        self.restart()
        second = self.service.accept(p.id, "K1")
        self.assertEqual(first, second)
        committed = [e for e in self.service.ledger.replay() if e.event_type == "ProposalCommitted"]
        self.assertEqual(1, len(committed))

    def test_committed_event_always_reconstructs_assignment(self):
        p = self.service.propose(1, 10, [10])
        self.service.accept(p.id, "K1")
        self.restart()
        for proposal in self.service.core.proposals.values():
            if proposal.status == ProposalStatus.COMMITTED:
                assignment = self.service.core.assignment_by_order.get(proposal.order_id)
                self.assertIsNotNone(assignment)
                self.assertEqual(proposal.id, assignment.proposal_id)

    def test_lapse_survives_restart(self):
        p = self.service.propose(1, 10, [10, 11])
        self.service.lapse(p.id)
        self.restart()
        self.assertEqual(ProposalStatus.LAPSED, self.service.core.proposals[p.id].status)
        p2 = self.service.propose(1, 11, [10, 11])
        self.assertEqual(11, p2.driver_id)

    def test_ledger_chain_remains_valid_across_full_lifecycle(self):
        p1 = self.service.propose(1, 10, [10, 11])
        self.service.decline(p1.id)
        p2 = self.service.propose(1, 11, [10, 11])
        self.service.accept(p2.id, "K2")
        self.assertTrue(self.service.ledger.verify_chain())
        self.restart()
        self.assertTrue(self.service.ledger.verify_chain())
        self.service.core.assert_invariants()


if __name__ == "__main__":
    unittest.main()
