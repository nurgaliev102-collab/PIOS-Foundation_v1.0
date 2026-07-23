import hashlib
import json
from dataclasses import dataclass
from typing import List

from concurrent_dispatch import ConcurrentDispatchService
from persistent_ledger import PersistentEventLedger
from stateful_dispatch import Assignment, DispatchConflict, Proposal, ProposalStatus, StatefulDispatchCore


@dataclass(frozen=True)
class FencedProposal:
    proposal: Proposal
    fence_token: int


class FencedDispatchService(ConcurrentDispatchService):
    """Persistent fencing with fail-closed recovery admission."""

    def _ensure_fencing_schema(self):
        self.ledger.conn.executescript("""
        CREATE TABLE IF NOT EXISTS order_fence (order_id INTEGER PRIMARY KEY,current_token INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS proposal_fence (proposal_id TEXT PRIMARY KEY,order_id INTEGER NOT NULL,fence_token INTEGER NOT NULL,UNIQUE(order_id,fence_token));
        CREATE TABLE IF NOT EXISTS proposal_terminal (proposal_id TEXT PRIMARY KEY,terminal_type TEXT NOT NULL CHECK(terminal_type IN ('COMMITTED','DECLINED','LAPSED')));
        """); self.ledger.conn.commit()

    def __init__(self,db_path):
        # Do not enter the legacy ConcurrentDispatchService constructor here:
        # it performs assignment-guard recovery before fenced admission and can
        # therefore either mask corruption with legacy errors or reject valid
        # multi-generation fenced history. Establish only the durable substrate,
        # create all schemas, prove bidirectional consistency, then recover using
        # fenced-generation semantics.
        self.db_path=db_path
        self.ledger=PersistentEventLedger(db_path)
        self.core=StatefulDispatchCore()
        self._ensure_concurrency_schema()
        self._ensure_fencing_schema()
        self.failpoint=None; self.fence_failpoint=None; self.terminal_failpoint=None
        self.validate_recovery_consistency()
        self._recover()

    def _recover(self):
        self.core = StatefulDispatchCore()
        for event in self.ledger.replay():
            p = event.payload
            if event.event_type == 'ProposalOffered':
                proposal = Proposal(p['proposal_id'], int(p['order_id']), int(p['driver_id']), ProposalStatus.OPEN)
                self.core.proposals[proposal.id] = proposal
                self.core.proposal_ids_by_order.setdefault(proposal.order_id, []).append(proposal.id)
                self.core._proposal_seq = max(self.core._proposal_seq, int(proposal.id[1:]))
            elif event.event_type == 'ProposalDeclined': self.core.proposals[p['proposal_id']].status = ProposalStatus.DECLINED
            elif event.event_type == 'ProposalLapsed': self.core.proposals[p['proposal_id']].status = ProposalStatus.LAPSED
            elif event.event_type == 'ProposalCommitted':
                proposal = self.core.proposals[p['proposal_id']]; assignment = Assignment(p['assignment_id'], proposal.order_id, proposal.driver_id, proposal.id)
                self.core.assignment_by_order[proposal.order_id] = assignment; proposal.status = ProposalStatus.COMMITTED
                self.core._assignment_seq = max(self.core._assignment_seq, int(assignment.id[1:]))
        for order_id, proposal_ids in self.core.proposal_ids_by_order.items():
            assert sum(self.core.proposals[pid].status == ProposalStatus.OPEN for pid in proposal_ids) <= 1, f'multiple OPEN proposals for order {order_id}'
            committed = [self.core.proposals[pid] for pid in proposal_ids if self.core.proposals[pid].status == ProposalStatus.COMMITTED]
            assert len(committed) <= 1, f'multiple COMMITTED proposals for order {order_id}'
            for proposal in committed:
                assignment = self.core.assignment_by_order.get(order_id); assert assignment is not None, f'orphan COMMITTED proposal {proposal.id}'
                assert assignment.proposal_id == proposal.id; assert assignment.driver_id == proposal.driver_id

    def validate_recovery_consistency(self):
        c=self.ledger.conn
        if not self.ledger.verify_chain(): raise DispatchConflict('EVENT_LEDGER_CHAIN_INVALID')
        for pf in c.execute('SELECT proposal_id,order_id,fence_token FROM proposal_fence').fetchall():
            offers=c.execute("SELECT payload_json FROM event_ledger WHERE event_type='ProposalOffered' AND json_extract(payload_json,'$.proposal_id')=?",(pf['proposal_id'],)).fetchall()
            if len(offers)!=1: raise DispatchConflict('PROPOSAL_FENCE_WITHOUT_OFFER_EVIDENCE')
            p=json.loads(offers[0]['payload_json'])
            if int(p['order_id'])!=pf['order_id'] or int(p.get('fence_token',-1))!=pf['fence_token']: raise DispatchConflict('PROPOSAL_FENCE_EVIDENCE_MISMATCH')
        for offered in c.execute("SELECT payload_json FROM event_ledger WHERE event_type='ProposalOffered'").fetchall():
            p=json.loads(offered['payload_json']); rows=c.execute('SELECT order_id,fence_token FROM proposal_fence WHERE proposal_id=?',(p['proposal_id'],)).fetchall()
            if len(rows)!=1: raise DispatchConflict('OFFER_EVIDENCE_WITHOUT_PROPOSAL_FENCE')
            if rows[0]['order_id']!=int(p['order_id']) or rows[0]['fence_token']!=int(p.get('fence_token',-1)): raise DispatchConflict('PROPOSAL_FENCE_EVIDENCE_MISMATCH')
        for of in c.execute('SELECT order_id,current_token FROM order_fence').fetchall():
            mx=c.execute('SELECT MAX(fence_token) m FROM proposal_fence WHERE order_id=?',(of['order_id'],)).fetchone()['m']
            if mx is None or mx!=of['current_token']: raise DispatchConflict('ORDER_FENCE_GENERATION_MISMATCH')
        for pf_order in c.execute('SELECT DISTINCT order_id FROM proposal_fence').fetchall():
            rows=c.execute('SELECT current_token FROM order_fence WHERE order_id=?',(pf_order['order_id'],)).fetchall()
            if len(rows)!=1: raise DispatchConflict('PROPOSAL_FENCE_WITHOUT_ORDER_FENCE')
        terminal_map={'COMMITTED':'ProposalCommitted','DECLINED':'ProposalDeclined','LAPSED':'ProposalLapsed'}
        inverse_terminal={v:k for k,v in terminal_map.items()}
        for t in c.execute('SELECT proposal_id,terminal_type FROM proposal_terminal').fetchall():
            events=c.execute("SELECT event_type FROM event_ledger WHERE json_extract(payload_json,'$.proposal_id')=? AND event_type IN ('ProposalCommitted','ProposalDeclined','ProposalLapsed')",(t['proposal_id'],)).fetchall()
            if len(events)!=1 or events[0]['event_type']!=terminal_map[t['terminal_type']]: raise DispatchConflict('TERMINAL_EVENT_MISMATCH')
            assignment=c.execute('SELECT 1 FROM assignment_guard WHERE proposal_id=?',(t['proposal_id'],)).fetchone()
            if (t['terminal_type']=='COMMITTED') != bool(assignment): raise DispatchConflict('TERMINAL_ASSIGNMENT_MISMATCH')
        terminal_events=c.execute("SELECT event_type,payload_json FROM event_ledger WHERE event_type IN ('ProposalCommitted','ProposalDeclined','ProposalLapsed')").fetchall()
        for event in terminal_events:
            p=json.loads(event['payload_json']); markers=c.execute('SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?',(p['proposal_id'],)).fetchall()
            if len(markers)!=1: raise DispatchConflict('TERMINAL_EVENT_WITHOUT_MARKER')
            if markers[0]['terminal_type']!=inverse_terminal[event['event_type']]: raise DispatchConflict('TERMINAL_EVENT_MISMATCH')
        for a in c.execute('SELECT order_id,assignment_id,proposal_id,driver_id,idempotency_key FROM assignment_guard').fetchall():
            events=c.execute("SELECT payload_json FROM event_ledger WHERE event_type='ProposalCommitted' AND json_extract(payload_json,'$.proposal_id')=?",(a['proposal_id'],)).fetchall()
            marker=c.execute("SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?",(a['proposal_id'],)).fetchone()
            if len(events)!=1 or marker is None or marker['terminal_type']!='COMMITTED': raise DispatchConflict('ASSIGNMENT_WITHOUT_COMMITTED_EVIDENCE')
            p=json.loads(events[0]['payload_json'])
            if int(p['order_id'])!=a['order_id'] or int(p['driver_id'])!=a['driver_id'] or p['assignment_id']!=a['assignment_id'] or p.get('idempotency_key')!=a['idempotency_key']: raise DispatchConflict('ASSIGNMENT_EVIDENCE_MISMATCH')
        return True

    @staticmethod
    def _next_proposal_id(c):
        rows=c.execute('SELECT proposal_id FROM proposal_fence').fetchall(); return f"P{max((int(r['proposal_id'][1:]) for r in rows),default=0)+1}"
    @staticmethod
    def _durable_proposal_status(c,pid):
        r=c.execute('SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?',(pid,)).fetchone()
        if r:return ProposalStatus(r['terminal_type'])
        if c.execute('SELECT 1 FROM assignment_guard WHERE proposal_id=?',(pid,)).fetchone():return ProposalStatus.COMMITTED
        rows=c.execute("SELECT event_type FROM event_ledger WHERE json_extract(payload_json,'$.proposal_id')=? ORDER BY seq",(pid,)).fetchall(); s=ProposalStatus.OPEN
        for e in rows:
            if e['event_type']=='ProposalDeclined':s=ProposalStatus.DECLINED
            elif e['event_type']=='ProposalLapsed':s=ProposalStatus.LAPSED
            elif e['event_type']=='ProposalCommitted':s=ProposalStatus.COMMITTED
        return s
    @staticmethod
    def _insert_event(c,eid,etype,agg,payload):
        pj=json.dumps(payload,sort_keys=True,separators=(',',':'),ensure_ascii=True); last=c.execute('SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1').fetchone(); prev=last['event_hash'] if last else 'GENESIS'; h=hashlib.sha256('|'.join((prev,eid,etype,agg,pj)).encode()).hexdigest(); c.execute('INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)',(eid,etype,agg,pj,prev,h))
    def _assert_current_fence_row(self,c,pid,token):
        r=c.execute('SELECT pf.order_id,pf.fence_token,of.current_token FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id WHERE pf.proposal_id=?',(pid,)).fetchone()
        if r is None:raise DispatchConflict('UNKNOWN_PROPOSAL_FENCE')
        if r['fence_token']!=token or r['current_token']!=token:raise DispatchConflict('STALE_PROPOSAL_FENCE')
        return r

    def propose_fenced(self,order_id,driver_id,eligible_driver_ids:List[int],reason='fairness'):
        self._recover();self._recover_assignment_guards();c=self.ledger.conn;c.execute('BEGIN IMMEDIATE')
        try:
            if c.execute('SELECT 1 FROM assignment_guard WHERE order_id=?',(order_id,)).fetchone():raise DispatchConflict('ORDER_ALREADY_ASSIGNED')
            cur=c.execute('SELECT pf.proposal_id FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id AND of.current_token=pf.fence_token WHERE pf.order_id=?',(order_id,)).fetchone()
            if cur and self._durable_proposal_status(c,cur['proposal_id'])==ProposalStatus.OPEN:raise DispatchConflict('OPEN_PROPOSAL_EXISTS')
            r=c.execute('SELECT current_token FROM order_fence WHERE order_id=?',(order_id,)).fetchone();token=r['current_token']+1 if r else 1;pid=self._next_proposal_id(c);did=f'DR-{order_id}-G{token}'
            c.execute('INSERT INTO order_fence(order_id,current_token) VALUES(?,?) ON CONFLICT(order_id) DO UPDATE SET current_token=excluded.current_token',(order_id,token));c.execute('INSERT INTO proposal_fence(proposal_id,order_id,fence_token) VALUES(?,?,?)',(pid,order_id,token));ej=json.dumps({'ids':[str(x) for x in eligible_driver_ids]},sort_keys=True,separators=(',',':'));dp=json.dumps({'fence_token':token,'order_id':order_id,'selected_driver_id':driver_id},sort_keys=True,separators=(',',':'));c.execute('INSERT INTO decision_record(decision_id,order_id,selected_driver_id,eligible_driver_ids_json,reason,payload_json) VALUES(?,?,?,?,?,?)',(did,str(order_id),str(driver_id),ej,reason,dp))
            if self.fence_failpoint=='after_fence_before_event':raise RuntimeError('INJECTED_CRASH_AFTER_FENCE')
            self._insert_event(c,f'proposal-offered:{pid}','ProposalOffered',str(order_id),{'proposal_id':pid,'order_id':order_id,'driver_id':driver_id,'fence_token':token})
            if self.fence_failpoint=='after_event_before_commit':raise RuntimeError('INJECTED_CRASH_AFTER_PROPOSAL_EVENT')
            c.commit()
        except Exception:
            if c.in_transaction:c.rollback()
            raise
        self._recover();return FencedProposal(self.core.proposals[pid],token)

    def _terminal(self,pid,token,terminal):
        c=self.ledger.conn;c.execute('BEGIN IMMEDIATE')
        try:
            r=self._assert_current_fence_row(c,pid,token)
            if self._durable_proposal_status(c,pid)!=ProposalStatus.OPEN:raise DispatchConflict('PROPOSAL_NOT_OPEN')
            c.execute('INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,?)',(pid,terminal))
            if self.terminal_failpoint=='after_terminal_before_event':raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL')
            eid,etype=(f'proposal-declined:{pid}','ProposalDeclined') if terminal=='DECLINED' else (f'proposal-lapsed:{pid}','ProposalLapsed');self._insert_event(c,eid,etype,str(r['order_id']),{'proposal_id':pid})
            if self.terminal_failpoint=='after_event_before_commit':raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL_EVENT')
            c.commit()
        except Exception:
            if c.in_transaction:c.rollback()
            raise
        self._recover()

    def accept_fenced(self,pid,token,key):
        self._recover();self._recover_assignment_guards();c=self.ledger.conn;c.execute('BEGIN IMMEDIATE')
        try:
            fence=self._assert_current_fence_row(c,pid,token);t=c.execute('SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?',(pid,)).fetchone()
            if t:
                if t['terminal_type']!='COMMITTED':raise DispatchConflict('PROPOSAL_NOT_OPEN')
                ex=c.execute('SELECT * FROM assignment_guard WHERE proposal_id=?',(pid,)).fetchone()
                if ex is None:raise DispatchConflict('COMMITTED_WITHOUT_ASSIGNMENT')
                if ex['idempotency_key']!=key:raise DispatchConflict('IDEMPOTENCY_KEY_MISMATCH')
                c.commit();return Assignment(ex['assignment_id'],ex['order_id'],ex['driver_id'],ex['proposal_id'])
            if self._durable_proposal_status(c,pid)!=ProposalStatus.OPEN:raise DispatchConflict('PROPOSAL_NOT_OPEN')
            if c.execute('SELECT * FROM assignment_guard WHERE order_id=?',(fence['order_id'],)).fetchone():raise DispatchConflict('ORDER_ALREADY_ASSIGNED')
            offered=c.execute("SELECT payload_json FROM event_ledger WHERE event_type='ProposalOffered' AND json_extract(payload_json,'$.proposal_id')=?",(pid,)).fetchone()
            if offered is None:raise DispatchConflict('PROPOSAL_EVIDENCE_MISSING')
            p=json.loads(offered['payload_json']);driver_id=int(p['driver_id']);order_id=int(p['order_id']);mr=c.execute("SELECT assignment_id FROM assignment_guard ORDER BY CAST(SUBSTR(assignment_id,2) AS INTEGER) DESC LIMIT 1").fetchone();aid=f"A{int(mr['assignment_id'][1:])+1 if mr else 1}"
            c.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,'COMMITTED')",(pid,));c.execute('INSERT INTO assignment_guard(order_id,assignment_id,proposal_id,driver_id,idempotency_key) VALUES(?,?,?,?,?)',(order_id,aid,pid,driver_id,key))
            if self.terminal_failpoint=='after_terminal_before_event':raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL')
            self._insert_event(c,f'proposal-committed:{pid}','ProposalCommitted',str(order_id),{'proposal_id':pid,'assignment_id':aid,'order_id':order_id,'driver_id':driver_id,'idempotency_key':key})
            if self.terminal_failpoint=='after_event_before_commit':raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL_EVENT')
            c.commit()
        except Exception:
            if c.in_transaction:c.rollback()
            raise
        self._recover();self._recover_assignment_guards();return self.core.assignment_by_order[order_id]

    def decline_fenced(self,pid,token):self._terminal(pid,token,'DECLINED')
    def lapse_fenced(self,pid,token):self._terminal(pid,token,'LAPSED')
