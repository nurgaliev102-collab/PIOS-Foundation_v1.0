import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict, ProposalStatus


class FencedDispatchTests(unittest.TestCase):
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

    def restart(self):
        self.s.close()
        self.s = FencedDispatchService(self.path)

    def test_first_proposal_gets_fence_one(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.assertEqual(1, fp.fence_token)
        self.assertEqual(10, fp.proposal.driver_id)

    def test_late_accept_after_lapse_is_rejected(self):
        first = self.s.propose_fenced(1, 10, [10, 11])
        self.s.lapse_fenced(first.proposal.id, first.fence_token)
        second = self.s.propose_fenced(1, 11, [10, 11])
        self.assertEqual(2, second.fence_token)
        with self.assertRaisesRegex(DispatchConflict, "STALE_PROPOSAL_FENCE"):
            self.s.accept_fenced(first.proposal.id, first.fence_token, "late-K1")
        assignment = self.s.accept_fenced(second.proposal.id, second.fence_token, "K2")
        self.assertEqual(11, assignment.driver_id)

    def test_wrong_token_cannot_accept_current_proposal(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.assertRaisesRegex(DispatchConflict, "STALE_PROPOSAL_FENCE"):
            self.s.accept_fenced(fp.proposal.id, fp.fence_token + 1, "K")

    def test_decline_then_redispatch_fences_old_generation(self):
        first = self.s.propose_fenced(1, 10, [10, 11])
        self.s.decline_fenced(first.proposal.id, first.fence_token)
        second = self.s.propose_fenced(1, 11, [10, 11])
        with self.assertRaisesRegex(DispatchConflict, "STALE_PROPOSAL_FENCE"):
            self.s.decline_fenced(first.proposal.id, first.fence_token)
        self.assertEqual(ProposalStatus.OPEN, self.s.core.proposals[second.proposal.id].status)

    def test_fence_survives_restart(self):
        first = self.s.propose_fenced(1, 10, [10, 11])
        self.s.lapse_fenced(first.proposal.id, first.fence_token)
        second = self.s.propose_fenced(1, 11, [10, 11])
        self.restart()
        with self.assertRaisesRegex(DispatchConflict, "STALE_PROPOSAL_FENCE"):
            self.s.accept_fenced(first.proposal.id, first.fence_token, "late")
        a = self.s.accept_fenced(second.proposal.id, second.fence_token, "current")
        self.assertEqual(11, a.driver_id)

    def test_committed_order_cannot_receive_new_generation(self):
        fp = self.s.propose_fenced(1, 10, [10, 11])
        self.s.accept_fenced(fp.proposal.id, fp.fence_token, "K1")
        with self.assertRaisesRegex(DispatchConflict, "ORDER_ALREADY_ASSIGNED"):
            self.s.propose_fenced(1, 11, [10, 11])


if __name__ == "__main__":
    unittest.main()
