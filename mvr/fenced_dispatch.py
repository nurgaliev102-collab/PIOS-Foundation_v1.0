import hashlib
import json
from dataclasses import dataclass
from typing import List

from concurrent_dispatch import ConcurrentDispatchService
from stateful_dispatch import Assignment, DispatchConflict, Proposal, ProposalStatus


@dataclass(frozen=True)
class FencedProposal:
    proposal: Proposal
    fence_token: int


class FencedDispatchService(ConcurrentDispatchService):
    """Persistent fencing with one serialized durable terminal lifecycle boundary."""

    def _ensure_fencing_schema(self) -> None:
        self.ledger.conn.executescript("""
        CREATE TABLE IF NOT EXISTS order_fence (order_id INTEGER PRIMARY KEY,current_token INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS proposal_fence (proposal_id TEXT PRIMARY KEY,order_id INTEGER NOT NULL,fence_token INTEGER NOT NULL,UNIQUE(order_id,fence_token));
        CREATE TABLE IF NOT EXISTS proposal_terminal (proposal_id TEXT PRIMARY KEY,terminal_type TEXT NOT NULL CHECK(terminal_type IN ('COMMITTED','DECLINED','LAPSED')));
        """); self.ledger.conn.commit()

    def __init__(self, db_path: str):
        super().__init__(db_path); self._ensure_fencing_schema(); self.fence_failpoint=None; self.terminal_failpoint=None

    @staticmethod
    def _next_proposal_id(conn):
        rows=conn.execute("SELECT proposal_id FROM proposal_fence").fetchall(); n=max((int(r['proposal_id'][1:]) for r in rows),default=0); return f"P{n+1}"

    @staticmethod
    def _durable_proposal_status(conn, proposal_id):
        row=conn.execute("SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?",(proposal_id,)).fetchone()
        if row: return ProposalStatus(row['terminal_type'])
        if conn.execute("SELECT 1 FROM assignment_guard WHERE proposal_id=?",(proposal_id,)).fetchone(): return ProposalStatus.COMMITTED
        rows=conn.execute("SELECT event_type FROM event_ledger WHERE json_extract(payload_json,'$.proposal_id')=? ORDER BY seq",(proposal_id,)).fetchall()
        status=ProposalStatus.OPEN
        for event in rows:
            if event['event_type']=='ProposalDeclined': status=ProposalStatus.DECLINED
            elif event['event_type']=='ProposalLapsed': status=ProposalStatus.LAPSED
            elif event['event_type']=='ProposalCommitted': status=ProposalStatus.COMMITTED
        return status

    @staticmethod
    def _insert_event(conn,event_id,event_type,aggregate_id,payload):
        payload_json=json.dumps(payload,sort_keys=True,separators=(",",":"),ensure_ascii=True)
        last=conn.execute("SELECT event_hash FROM event_ledger ORDER BY seq DESC LIMIT 1").fetchone(); prev=last['event_hash'] if last else 'GENESIS'
        h=hashlib.sha256('|'.join((prev,event_id,event_type,aggregate_id,payload_json)).encode()).hexdigest()
        conn.execute("INSERT INTO event_ledger(event_id,event_type,aggregate_id,payload_json,prev_hash,event_hash) VALUES(?,?,?,?,?,?)",(event_id,event_type,aggregate_id,payload_json,prev,h))

    def _assert_current_fence_row(self,conn,proposal_id,fence_token):
        row=conn.execute("SELECT pf.order_id,pf.fence_token,of.current_token FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id WHERE pf.proposal_id=?",(proposal_id,)).fetchone()
        if row is None: raise DispatchConflict('UNKNOWN_PROPOSAL_FENCE')
        if row['fence_token']!=fence_token or row['current_token']!=fence_token: raise DispatchConflict('STALE_PROPOSAL_FENCE')
        return row

    def propose_fenced(self,order_id,driver_id,eligible_driver_ids:List[int],reason='fairness'):
        self._recover(); self._recover_assignment_guards(); conn=self.ledger.conn; conn.execute('BEGIN IMMEDIATE')
        try:
            if conn.execute('SELECT 1 FROM assignment_guard WHERE order_id=?',(order_id,)).fetchone(): raise DispatchConflict('ORDER_ALREADY_ASSIGNED')
            current=conn.execute("SELECT pf.proposal_id FROM proposal_fence pf JOIN order_fence of ON of.order_id=pf.order_id AND of.current_token=pf.fence_token WHERE pf.order_id=?",(order_id,)).fetchone()
            if current and self._durable_proposal_status(conn,current['proposal_id'])==ProposalStatus.OPEN: raise DispatchConflict('OPEN_PROPOSAL_EXISTS')
            row=conn.execute('SELECT current_token FROM order_fence WHERE order_id=?',(order_id,)).fetchone(); token=row['current_token']+1 if row else 1
            pid=self._next_proposal_id(conn); did=f'DR-{order_id}-G{token}'
            conn.execute('INSERT INTO order_fence(order_id,current_token) VALUES(?,?) ON CONFLICT(order_id) DO UPDATE SET current_token=excluded.current_token',(order_id,token))
            conn.execute('INSERT INTO proposal_fence(proposal_id,order_id,fence_token) VALUES(?,?,?)',(pid,order_id,token))
            ej=json.dumps({'ids':[str(x) for x in eligible_driver_ids]},sort_keys=True,separators=(",",":")); dp=json.dumps({'fence_token':token,'order_id':order_id,'selected_driver_id':driver_id},sort_keys=True,separators=(",",":"))
            conn.execute('INSERT INTO decision_record(decision_id,order_id,selected_driver_id,eligible_driver_ids_json,reason,payload_json) VALUES(?,?,?,?,?,?)',(did,str(order_id),str(driver_id),ej,reason,dp))
            if self.fence_failpoint=='after_fence_before_event': raise RuntimeError('INJECTED_CRASH_AFTER_FENCE')
            self._insert_event(conn,f'proposal-offered:{pid}','ProposalOffered',str(order_id),{'proposal_id':pid,'order_id':order_id,'driver_id':driver_id,'fence_token':token})
            if self.fence_failpoint=='after_event_before_commit': raise RuntimeError('INJECTED_CRASH_AFTER_PROPOSAL_EVENT')
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        self._recover(); return FencedProposal(self.core.proposals[pid],token)

    def _terminal(self,proposal_id,fence_token,terminal):
        conn=self.ledger.conn; conn.execute('BEGIN IMMEDIATE')
        try:
            row=self._assert_current_fence_row(conn,proposal_id,fence_token)
            if self._durable_proposal_status(conn,proposal_id)!=ProposalStatus.OPEN: raise DispatchConflict('PROPOSAL_NOT_OPEN')
            conn.execute('INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,?)',(proposal_id,terminal))
            if self.terminal_failpoint=='after_terminal_before_event': raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL')
            if terminal=='DECLINED': event_id=f'proposal-declined:{proposal_id}'; event_type='ProposalDeclined'
            else: event_id=f'proposal-lapsed:{proposal_id}'; event_type='ProposalLapsed'
            self._insert_event(conn,event_id,event_type,str(row['order_id']),{'proposal_id':proposal_id})
            if self.terminal_failpoint=='after_event_before_commit': raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL_EVENT')
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        self._recover()

    def accept_fenced(self,proposal_id,fence_token,idempotency_key):
        self._recover(); self._recover_assignment_guards(); conn=self.ledger.conn; conn.execute('BEGIN IMMEDIATE')
        try:
            fence=self._assert_current_fence_row(conn,proposal_id,fence_token)
            terminal=conn.execute('SELECT terminal_type FROM proposal_terminal WHERE proposal_id=?',(proposal_id,)).fetchone()
            if terminal:
                if terminal['terminal_type']!='COMMITTED': raise DispatchConflict('PROPOSAL_NOT_OPEN')
                existing=conn.execute('SELECT * FROM assignment_guard WHERE proposal_id=?',(proposal_id,)).fetchone()
                if existing is None: raise DispatchConflict('COMMITTED_WITHOUT_ASSIGNMENT')
                if existing['idempotency_key']!=idempotency_key: raise DispatchConflict('IDEMPOTENCY_KEY_MISMATCH')
                conn.commit(); return Assignment(existing['assignment_id'],existing['order_id'],existing['driver_id'],existing['proposal_id'])
            if self._durable_proposal_status(conn,proposal_id)!=ProposalStatus.OPEN: raise DispatchConflict('PROPOSAL_NOT_OPEN')
            existing=conn.execute('SELECT * FROM assignment_guard WHERE order_id=?',(fence['order_id'],)).fetchone()
            if existing: raise DispatchConflict('ORDER_ALREADY_ASSIGNED')
            offered=conn.execute("SELECT payload_json FROM event_ledger WHERE event_type='ProposalOffered' AND json_extract(payload_json,'$.proposal_id')=?",(proposal_id,)).fetchone()
            if offered is None: raise DispatchConflict('PROPOSAL_EVIDENCE_MISSING')
            payload=json.loads(offered['payload_json']); driver_id=int(payload['driver_id']); order_id=int(payload['order_id'])
            maxrow=conn.execute("SELECT assignment_id FROM assignment_guard ORDER BY CAST(SUBSTR(assignment_id,2) AS INTEGER) DESC LIMIT 1").fetchone(); seq=int(maxrow['assignment_id'][1:])+1 if maxrow else 1; aid=f'A{seq}'
            conn.execute("INSERT INTO proposal_terminal(proposal_id,terminal_type) VALUES(?,'COMMITTED')",(proposal_id,))
            conn.execute('INSERT INTO assignment_guard(order_id,assignment_id,proposal_id,driver_id,idempotency_key) VALUES(?,?,?,?,?)',(order_id,aid,proposal_id,driver_id,idempotency_key))
            if self.terminal_failpoint=='after_terminal_before_event': raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL')
            self._insert_event(conn,f'proposal-committed:{proposal_id}','ProposalCommitted',str(order_id),{'proposal_id':proposal_id,'assignment_id':aid,'order_id':order_id,'driver_id':driver_id,'idempotency_key':idempotency_key})
            if self.terminal_failpoint=='after_event_before_commit': raise RuntimeError('INJECTED_CRASH_AFTER_TERMINAL_EVENT')
            conn.commit()
        except Exception:
            if conn.in_transaction: conn.rollback()
            raise
        self._recover(); self._recover_assignment_guards(); return self.core.assignment_by_order[order_id]

    def decline_fenced(self,proposal_id,fence_token): self._terminal(proposal_id,fence_token,'DECLINED')
    def lapse_fenced(self,proposal_id,fence_token): self._terminal(proposal_id,fence_token,'LAPSED')
