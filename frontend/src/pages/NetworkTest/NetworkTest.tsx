import { useState } from 'react'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { ApiError, request } from '../../api/apiClient'
import styles from './NetworkTest.module.css'

// Network Management's own local port (INTERFACE_CONTRACTS.md-equivalent
// convention: driver-management 8081, passenger-experience 8082,
// order-management 8083, dispatch 8084 -- network-management is 8085).
const NETWORK_MANAGEMENT_BASE_URL = import.meta.env.VITE_NETWORK_MANAGEMENT_BASE_URL ?? 'http://localhost:8085'

interface PersonRecord {
  id: string
  name: string
}

interface ConnectionListItem {
  person: string
  type: string
}

type Status = 'idle' | 'submitting' | 'error'

/**
 * Network Test — Sprint 7A: PIOS Network Foundation.
 *
 * Not a real product screen: a minimal, internal page to exercise
 * Network Management's own backend directly (Create Person, Create
 * Connection, view a person's connections), per this sprint's own scope
 * ("проверить работу backend", not a polished user interface). Every
 * person created this session is kept in local component state only, so
 * this page can offer a person picker without requiring a
 * `GET /v1/persons` list endpoint that was never asked for.
 */
export function NetworkTest() {
  const [people, setPeople] = useState<PersonRecord[]>([])

  const [name, setName] = useState('')
  const [createPersonStatus, setCreatePersonStatus] = useState<Status>('idle')
  const [createPersonError, setCreatePersonError] = useState<string | null>(null)

  const [fromPersonId, setFromPersonId] = useState('')
  const [toPersonId, setToPersonId] = useState('')
  const [createConnectionStatus, setCreateConnectionStatus] = useState<Status>('idle')
  const [createConnectionError, setCreateConnectionError] = useState<string | null>(null)

  const [viewPersonId, setViewPersonId] = useState('')
  const [connections, setConnections] = useState<ConnectionListItem[] | null>(null)
  const [viewStatus, setViewStatus] = useState<Status>('idle')
  const [viewError, setViewError] = useState<string | null>(null)

  async function handleCreatePerson() {
    const trimmed = name.trim()
    if (!trimmed || createPersonStatus === 'submitting') {
      return
    }
    setCreatePersonStatus('submitting')
    setCreatePersonError(null)
    try {
      const person = await request<{ id: string; name: string }>('/v1/persons', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: trimmed }),
        baseUrl: NETWORK_MANAGEMENT_BASE_URL,
      })
      setPeople((current) => [...current, { id: person.id, name: person.name }])
      setName('')
      setCreatePersonStatus('idle')
    } catch {
      setCreatePersonError('Could not create person.')
      setCreatePersonStatus('error')
    }
  }

  async function handleCreateConnection() {
    if (!fromPersonId || !toPersonId || createConnectionStatus === 'submitting') {
      return
    }
    setCreateConnectionStatus('submitting')
    setCreateConnectionError(null)
    try {
      await request('/v1/connections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fromPersonId, toPersonId, type: 'CONNECTED' }),
        baseUrl: NETWORK_MANAGEMENT_BASE_URL,
      })
      setCreateConnectionStatus('idle')
    } catch (error) {
      setCreateConnectionError(
        error instanceof ApiError && error.status === 400
          ? 'A person cannot connect to themselves.'
          : 'Could not create connection.'
      )
      setCreateConnectionStatus('error')
    }
  }

  async function handleViewConnections() {
    if (!viewPersonId || viewStatus === 'submitting') {
      return
    }
    setViewStatus('submitting')
    setViewError(null)
    try {
      const result = await request<ConnectionListItem[]>(`/v1/persons/${viewPersonId}/connections`, {
        baseUrl: NETWORK_MANAGEMENT_BASE_URL,
      })
      setConnections(result)
      setViewStatus('idle')
    } catch {
      setViewError('Could not load connections.')
      setViewStatus('error')
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <h1 className={styles.title}>Network Test</h1>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Create Person</h2>
          <input
            className={styles.input}
            type="text"
            value={name}
            placeholder="Name (e.g. Артур)"
            aria-label="Name"
            onChange={(event) => setName(event.target.value)}
          />
          <ActionButton
            label={createPersonStatus === 'submitting' ? 'Creating…' : 'Create Person'}
            variant="primary"
            onClick={handleCreatePerson}
            disabled={!name.trim() || createPersonStatus === 'submitting'}
          />
          {createPersonError && (
            <p className={styles.error} role="alert">
              {createPersonError}
            </p>
          )}
          {people.map((person) => (
            <div key={person.id} className={styles.personRow}>
              <span className={styles.personName}>{person.name}</span>
              <span className={styles.personId}>{person.id}</span>
            </div>
          ))}
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Create Connection</h2>
          <select
            className={styles.select}
            value={fromPersonId}
            aria-label="From person"
            onChange={(event) => setFromPersonId(event.target.value)}
          >
            <option value="">From…</option>
            {people.map((person) => (
              <option key={person.id} value={person.id}>
                {person.name}
              </option>
            ))}
          </select>
          <select
            className={styles.select}
            value={toPersonId}
            aria-label="To person"
            onChange={(event) => setToPersonId(event.target.value)}
          >
            <option value="">To…</option>
            {people.map((person) => (
              <option key={person.id} value={person.id}>
                {person.name}
              </option>
            ))}
          </select>
          <ActionButton
            label={createConnectionStatus === 'submitting' ? 'Connecting…' : 'Create Connection'}
            variant="primary"
            onClick={handleCreateConnection}
            disabled={!fromPersonId || !toPersonId || createConnectionStatus === 'submitting'}
          />
          {createConnectionError && (
            <p className={styles.error} role="alert">
              {createConnectionError}
            </p>
          )}
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>View Connections</h2>
          <select
            className={styles.select}
            value={viewPersonId}
            aria-label="Person"
            onChange={(event) => setViewPersonId(event.target.value)}
          >
            <option value="">Person…</option>
            {people.map((person) => (
              <option key={person.id} value={person.id}>
                {person.name}
              </option>
            ))}
          </select>
          <ActionButton
            label={viewStatus === 'submitting' ? 'Loading…' : 'View Connections'}
            variant="secondary"
            onClick={handleViewConnections}
            disabled={!viewPersonId || viewStatus === 'submitting'}
          />
          {viewError && (
            <p className={styles.error} role="alert">
              {viewError}
            </p>
          )}
          {connections && connections.length === 0 && <p className={styles.status}>No connections yet.</p>}
          {connections?.map((connection, index) => (
            <div key={index} className={styles.connectionRow}>
              <span>{connection.person}</span>
              <span>{connection.type}</span>
            </div>
          ))}
        </section>
      </main>
    </div>
  )
}
