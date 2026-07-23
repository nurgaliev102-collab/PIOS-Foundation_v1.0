import os
import tempfile
import threading
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class AtomicTerminalTransitionTests(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix='.db'); os.close(fd)
        self.a = FencedDispatchService(self.path)
        self.b = FencedDispatchService(self.path)

    def tearDown(self):
        for s in (self.a, self.b):
            try: s.close()
            except Exception: pass
        for suffix in ('', '-wal', '-shm'):
            try: os.remove(self.path + suffix)
            except FileNotFoundError: pass

    def race(self, left, right):
        barrier = threading.Barrier(2); wins=[]; errors=[]
        def run(fn):
            try: barrier.wait(); fn(); wins.append(True)
            except Exception as exc: errors.append(exc)
        t1=threading.Thread(target=run,args=(left,)); t2=threading.Thread(target=run,args=(right,))
        t1.start(); t2.start(); t1.join(); t2.join()
        return wins, errors

    def test_decline_vs_lapse_exactly_one_terminal_wins(self):
        fp=self.a.propose_fenced(1,10,[10])
        wins,errors=self.race(lambda:self.a.decline_fenced(fp.proposal.id,fp.fence_token), lambda:self.b.lapse_fenced(fp.proposal.id,fp.fence_token))
        self.assertEqual(1,len(wins)); self.assertEqual(1,len(errors)); self.assertIsInstance(errors[0],DispatchConflict)
        self.assertEqual(1,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal WHERE proposal_id=?',(fp.proposal.id,)).fetchone()[0])
        terminal_events=self.a.ledger.conn.execute("SELECT COUNT(*) FROM event_ledger WHERE event_type IN ('ProposalDeclined','ProposalLapsed')").fetchone()[0]
        self.assertEqual(1,terminal_events); self.assertTrue(self.a.ledger.verify_chain())

    def test_decline_crash_rolls_back_terminal_and_event(self):
        fp=self.a.propose_fenced(1,10,[10]); before=self.a.ledger.count_events()
        self.a.terminal_failpoint='after_terminal_before_event'
        with self.assertRaises(RuntimeError): self.a.decline_fenced(fp.proposal.id,fp.fence_token)
        self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal').fetchone()[0])
        self.assertEqual(before,self.a.ledger.count_events())
        self.a.terminal_failpoint=None; self.a.decline_fenced(fp.proposal.id,fp.fence_token)

    def test_lapse_crash_after_event_rolls_back_both(self):
        fp=self.a.propose_fenced(1,10,[10]); before=self.a.ledger.count_events()
        self.a.terminal_failpoint='after_event_before_commit'
        with self.assertRaises(RuntimeError): self.a.lapse_fenced(fp.proposal.id,fp.fence_token)
        self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal').fetchone()[0])
        self.assertEqual(before,self.a.ledger.count_events()); self.assertTrue(self.a.ledger.verify_chain())


if __name__=='__main__': unittest.main()
