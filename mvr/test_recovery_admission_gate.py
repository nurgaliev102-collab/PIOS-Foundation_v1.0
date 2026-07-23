import os
import tempfile
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class RecoveryAdmissionGateTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix='.db'); os.close(fd)
        self.s = FencedDispatchService(self.path)

    def tearDown(self):
        try: self.s.close()
        except Exception: pass
        for suffix in ('', '-wal', '-shm'):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def restart_must_fail(self):
        self.s.close()
        with self.assertRaises(DispatchConflict):
            FencedDispatchService(self.path)

    def test_corrupt_fence_generation_blocks_restart_admission(self):
        self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute('UPDATE order_fence SET current_token=99 WHERE order_id=1')
        self.restart_must_fail()

    def test_terminal_event_disagreement_blocks_restart_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.decline_fenced(fp.proposal.id, fp.fence_token)
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("UPDATE proposal_terminal SET terminal_type='LAPSED' WHERE proposal_id=?", (fp.proposal.id,))
        self.restart_must_fail()

    def test_committed_without_assignment_blocks_restart_admission(self):
        fp = self.s.propose_fenced(1, 10, [10])
        with self.s.ledger.conn:
            self.s.ledger.conn.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,'COMMITTED')", (fp.proposal.id,))
        self.restart_must_fail()

    def test_valid_restart_remains_admitted(self):
        fp = self.s.propose_fenced(1, 10, [10])
        self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        self.s.close(); self.s = FencedDispatchService(self.path)
        recovered = self.s.accept_fenced(fp.proposal.id, fp.fence_token, 'K1')
        self.assertEqual(1, recovered.order_id)


if __name__ == '__main__': unittest.main()
