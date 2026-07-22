import unittest

from stateful_dispatch import DispatchConflict, ProposalStatus, StatefulDispatchCore


class Milestone3StatefulDispatchTests(unittest.TestCase):
    def setUp(self):
        self.core = StatefulDispatchCore()

    def test_decision_exists_before_proposal_event(self):
        p = self.core.propose(1, 10, [10, 11])
        self.assertEqual(1, len(self.core.decisions))
        self.assertEqual("ProposalOffered", self.core.events[0]["type"])
        self.assertEqual(10, self.core.decisions[0]["selected_driver_id"])
        self.assertEqual(p.id, self.core.events[0]["proposal_id"])

    def test_only_one_open_proposal_per_order(self):
        self.core.propose(1, 10, [10, 11])
        with self.assertRaisesRegex(DispatchConflict, "OPEN_PROPOSAL_EXISTS"):
            self.core.propose(1, 11, [10, 11])
        self.core.assert_invariants()

    def test_sequential_decline_then_next_candidate(self):
        first = self.core.propose(1, 10, [10, 11])
        self.core.decline(first.id)
        second = self.core.propose(1, 11, [10, 11])
        assignment = self.core.accept(second.id, "accept-1")
        self.assertEqual(11, assignment.driver_id)
        self.assertEqual(ProposalStatus.DECLINED, first.status)
        self.assertEqual(ProposalStatus.COMMITTED, second.status)
        self.core.assert_invariants()

    def test_timeout_then_next_candidate(self):
        first = self.core.propose(1, 10, [10, 11])
        self.core.lapse(first.id)
        second = self.core.propose(1, 11, [10, 11])
        self.assertEqual(11, second.driver_id)
        self.core.assert_invariants()

    def test_accept_is_idempotent(self):
        proposal = self.core.propose(1, 10, [10])
        a1 = self.core.accept(proposal.id, "same-key")
        a2 = self.core.accept(proposal.id, "same-key")
        self.assertEqual(a1, a2)
        self.assertEqual(1, len(self.core.assignment_by_order))
        committed_events = [e for e in self.core.events if e["type"] == "ProposalCommitted"]
        self.assertEqual(1, len(committed_events))
        self.core.assert_invariants()

    def test_committed_never_exists_without_assignment(self):
        proposal = self.core.propose(1, 10, [10])
        assignment = self.core.accept(proposal.id, "accept-1")
        self.assertEqual(ProposalStatus.COMMITTED, proposal.status)
        self.assertEqual(assignment, self.core.assignment_by_order[1])
        self.core.assert_invariants()

    def test_ineligible_driver_cannot_receive_proposal(self):
        with self.assertRaisesRegex(DispatchConflict, "DRIVER_NOT_ELIGIBLE"):
            self.core.propose(1, 99, [10, 11])
        self.assertEqual([], self.core.events)
        self.assertEqual([], self.core.decisions)

    def test_same_driver_cannot_be_retried_for_same_order(self):
        first = self.core.propose(1, 10, [10, 11])
        self.core.decline(first.id)
        with self.assertRaisesRegex(DispatchConflict, "DRIVER_ALREADY_ATTEMPTED"):
            self.core.propose(1, 10, [10, 11])
        self.core.assert_invariants()

    def test_order_cannot_receive_second_assignment(self):
        first = self.core.propose(1, 10, [10, 11])
        self.core.accept(first.id, "accept-1")
        with self.assertRaisesRegex(DispatchConflict, "ORDER_ALREADY_ASSIGNED"):
            self.core.propose(1, 11, [10, 11])
        self.core.assert_invariants()


if __name__ == "__main__":
    unittest.main()
