import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class BidirectionalRecoveryConsistencyTests(unittest.TestCase):
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

    def restart_must_fail(self, expected):
        self.s.close()
        with self.assertRaisesRegex(DispatchConflict, expected):
            FencedDispatchService(self.path)

    def test_orphan_offer_event_without_proposal_fence_blocks_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('DELETE FROM proposal_fence WHERE proposal_id=?', (fp.proposal.id,))
            self.s.ledger.conn.execute('DELETE FROM order_fence WHERE order_id=1')
        self.restart_must_fail('OFFER_EVIDENCE_WITHOUT_PROPOSAL_FENCE')

    def test_orphan_terminal_event_without_terminal_marker_blocks_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.decline_fenced(fp.proposal.id, fp.fence_token)
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('DELETE FROM proposal_terminal WHERE proposal_id=?', (fp.proposal.id,))
        self.restart_must_fail('TERMINAL_EVENT_WITHOUT_MARKER')

    def test_orphan_assignment_guard_without_committed_evidence_blocks_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute(
                'INSERT INTO assignment_guard(order_id,assignment_id,proposal_id,driver_id,idempotency_key) VALUES(?,?,?,?,?)',
                (1, 'A1', fp.proposal.id, 10, 'K1'),
            )
        self.restart_must_fail('ASSIGNMENT_WITHOUT_COMMITTED_EVIDENCE')

    def test_assignment_guard_payload_mismatch_blocks_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('UPDATE assignment_guard SET driver_id=99 WHERE proposal_id=?', (fp.proposal.id,))
        self.restart_must_fail('ASSIGNMENT_EVIDENCE_MISMATCH')

    def test_valid_multi_generation_redispatch_remains_admitted(self):
        first = self.s.propose_fenced(1, 10, [10, 11])
        self.s.lapse_fenced(first.proposal.id, first.fence_token)
        second = self.s.propose_fenced(1, 10, [10, 11])
        self.assertEqual(2, second.fence_token)
        self.s.close()
        self.s = FencedDispatchService(self.path)
        self.assertTrue(self.s.validate_recovery_consistency())


if __name__ == '__main__':
    unittest.main()
