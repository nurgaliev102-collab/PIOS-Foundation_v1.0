import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class RuntimeAdmissionContinuityTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix='.db')
        os.close(fd)
        self.s = FencedDispatchService(self.path)

    def tearDown(self):
        try:
            self.s.close()
        except Exception:
            pass
        for suffix in ('', '-wal', '-shm'):
            try:
                os.remove(self.path + suffix)
            except FileNotFoundError:
                pass

    def test_runtime_propose_fails_closed_if_durable_evidence_is_corrupted_after_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('DELETE FROM proposal_fence WHERE proposal_id=?', (fp.proposal.id,))
            self.s.ledger.conn.execute('DELETE FROM order_fence WHERE order_id=1')
        with self.assertRaisesRegex(DispatchConflict, 'OFFER_EVIDENCE_WITHOUT_PROPOSAL_FENCE'):
            self.s.propose_fenced(2, 20, [20])

    def test_runtime_accept_fails_closed_if_assignment_evidence_is_corrupted_after_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('UPDATE assignment_guard SET driver_id=99 WHERE proposal_id=?', (fp.proposal.id,))
        with self.assertRaisesRegex(DispatchConflict, 'ASSIGNMENT_EVIDENCE_MISMATCH'):
            self.s.propose_fenced(2, 20, [20])

    def test_runtime_terminal_fails_closed_if_terminal_marker_is_corrupted_after_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?, 'DECLINED')", (fp.proposal.id,))
        with self.assertRaisesRegex(DispatchConflict, 'TERMINAL_EVENT_MISMATCH'):
            self.s.lapse_fenced(fp.proposal.id, fp.fence_token)

    def test_valid_runtime_multi_generation_redispatch_continues(self):
        first = self.s.propose_fenced(1, 10, [10, 11])
        self.s.lapse_fenced(first.proposal.id, first.fence_token)
        second = self.s.propose_fenced(1, 10, [10, 11])
        self.assertEqual(2, second.fence_token)
        self.s.decline_fenced(second.proposal.id, second.fence_token)
        third = self.s.propose_fenced(1, 11, [10, 11])
        self.assertEqual(3, third.fence_token)


if __name__ == '__main__':
    unittest.main()
