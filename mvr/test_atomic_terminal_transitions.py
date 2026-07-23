import os
import tempfile
import threading
import unittest

from fenced_dispatch import FencedDispatchService
from stateful_dispatch import DispatchConflict


class AtomicTerminalTransitionTests(unittest.TestCase):
    def setUp(self):
        fd,self.path=tempfile.mkstemp(suffix='.db'); os.close(fd); self.a=FencedDispatchService(self.path); self.b=FencedDispatchService(self.path)
    def tearDown(self):
        for s in (self.a,self.b):
            try:s.close()
            except Exception:pass
        for suffix in ('','-wal','-shm'):
            try:os.remove(self.path+suffix)
            except FileNotFoundError:pass
    def race(self,left,right):
        barrier=threading.Barrier(2); wins=[]; errors=[]
        def run(fn):
            try:barrier.wait(); fn(); wins.append(True)
            except Exception as exc:errors.append(exc)
        t1=threading.Thread(target=run,args=(left,)); t2=threading.Thread(target=run,args=(right,)); t1.start();t2.start();t1.join();t2.join(); return wins,errors
    def assert_one_terminal(self,pid):
        self.assertEqual(1,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal WHERE proposal_id=?',(pid,)).fetchone()[0]); self.assertTrue(self.a.ledger.verify_chain())
    def test_decline_vs_lapse_exactly_one_terminal_wins(self):
        fp=self.a.propose_fenced(1,10,[10]); wins,errors=self.race(lambda:self.a.decline_fenced(fp.proposal.id,fp.fence_token),lambda:self.b.lapse_fenced(fp.proposal.id,fp.fence_token)); self.assertEqual(1,len(wins));self.assertEqual(1,len(errors));self.assertIsInstance(errors[0],DispatchConflict);self.assert_one_terminal(fp.proposal.id)
    def test_accept_vs_decline_exactly_one_terminal_wins(self):
        fp=self.a.propose_fenced(1,10,[10]); wins,errors=self.race(lambda:self.a.accept_fenced(fp.proposal.id,fp.fence_token,'K1'),lambda:self.b.decline_fenced(fp.proposal.id,fp.fence_token)); self.assertEqual(1,len(wins));self.assertEqual(1,len(errors));self.assertIsInstance(errors[0],DispatchConflict);self.assert_one_terminal(fp.proposal.id)
        terminal=self.a.ledger.conn.execute('SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?',(fp.proposal.id,)).fetchone()['terminal_type']; assignments=self.a.ledger.conn.execute('SELECT COUNT(*) FROM assignment_guard WHERE proposal_id=?',(fp.proposal.id,)).fetchone()[0]; self.assertEqual(1 if terminal=='COMMITTED' else 0,assignments)
    def test_accept_vs_lapse_exactly_one_terminal_wins(self):
        fp=self.a.propose_fenced(1,10,[10]); wins,errors=self.race(lambda:self.a.accept_fenced(fp.proposal.id,fp.fence_token,'K1'),lambda:self.b.lapse_fenced(fp.proposal.id,fp.fence_token)); self.assertEqual(1,len(wins));self.assertEqual(1,len(errors));self.assertIsInstance(errors[0],DispatchConflict);self.assert_one_terminal(fp.proposal.id)
    def test_decline_crash_rolls_back_terminal_and_event(self):
        fp=self.a.propose_fenced(1,10,[10]);before=self.a.ledger.count_events();self.a.terminal_failpoint='after_terminal_before_event'
        with self.assertRaises(RuntimeError):self.a.decline_fenced(fp.proposal.id,fp.fence_token)
        self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal').fetchone()[0]);self.assertEqual(before,self.a.ledger.count_events())
    def test_accept_crash_after_terminal_rolls_back_assignment_and_event(self):
        fp=self.a.propose_fenced(1,10,[10]);before=self.a.ledger.count_events();self.a.terminal_failpoint='after_terminal_before_event'
        with self.assertRaises(RuntimeError):self.a.accept_fenced(fp.proposal.id,fp.fence_token,'K1')
        self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal').fetchone()[0]);self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM assignment_guard').fetchone()[0]);self.assertEqual(before,self.a.ledger.count_events())
    def test_lapse_crash_after_event_rolls_back_both(self):
        fp=self.a.propose_fenced(1,10,[10]);before=self.a.ledger.count_events();self.a.terminal_failpoint='after_event_before_commit'
        with self.assertRaises(RuntimeError):self.a.lapse_fenced(fp.proposal.id,fp.fence_token)
        self.assertEqual(0,self.a.ledger.conn.execute('SELECT COUNT(*) FROM proposal_terminal').fetchone()[0]);self.assertEqual(before,self.a.ledger.count_events());self.assertTrue(self.a.ledger.verify_chain())

if __name__=='__main__':unittest.main()
