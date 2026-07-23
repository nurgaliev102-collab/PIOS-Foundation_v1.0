import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class RecoveryTerminalConsistencyTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix='.db'); os.close(fd)
        self.s = FencedDispatchService(self.path)

    def tearDown(self):
        try: self.s.close()
        except Exception: pass
        for suffix in ('', '-wal', '-shm'):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def restart(self):
        self.s.close(); self.s = FencedDispatchService(self.path)

    def test_committed_state_survives_restart_consistently(self):
        fp = self.s.propose_fenced(1, 10, [10])
        a = self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        self.restart()
        recovered = self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        self.assertEqual(a, recovered)
        self.assertTrue(self.s.ledger.verify_chain())

    def test_declined_state_survives_restart_and_blocks_accept(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.decline_fenced(fp.proposal.id, fp.fence_token)
        self.restart()
        with self.assertRaisesRegex(DispatchConflict, 'PROPOSAL_NOT_OPEN'):
            self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')

    def test_lapsed_state_survives_restart_and_allows_next_generation(self):
        fp = self.s.propose_fenced(1, 10, [10, 11])
        self.s.lapse_fenced(fp.proposal.id, fp.fence_token)
        self.restart()
        second = self.s.propose_fenced(1, 11, [10, 11])
        self.assertEqual(2, second.fence_token)

    def test_committed_terminal_without_assignment_fails_closed(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,'COMMITTED')", (fp.proposal.id,))
        self.restart()
        with self.assertRaisesRegex(DispatchConflict, 'COMMITTED_WITHOUT_ASSIGNMENT'):
            self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')

    def test_terminal_event_disagreement_is_detectable(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.decline_fenced(fp.proposal.id, fp.fence_token)
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("UPDATE proposal_terminal SET terminal_type='LAPSED' WHERE proposal_id=?", (fp.proposal.id,))
        with self.assertRaises(DispatchConflict):
            self.s.validate_recovery_consistency()

    def test_fence_generation_mismatch_is_detectable(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("UPDATE order_fence SET current_token=99 WHERE order_id=1")
        with self.assertRaises(DispatchConflict):
            self.s.validate_recovery_consistency()


if __name__ == '__main__': unittest.main()
