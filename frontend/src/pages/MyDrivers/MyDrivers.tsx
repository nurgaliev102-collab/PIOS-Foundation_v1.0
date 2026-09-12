import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Header } from '../../components/Header'
import { Heading } from '../../components/Heading'
import { Text } from '../../components/Text'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { LoadingState } from '../../components/LoadingState'
import { ErrorState } from '../../components/ErrorState'
import { DriverTrustIndicator } from '../../components/DriverTrustIndicator'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import { request, resolveBackendBaseUrl } from '../../api/apiClient'
import styles from './MyDrivers.module.css'

// ADR-038/ADR-039/ADR-055: same singleton pattern DriverHome.tsx/PassengerLanding.tsx
// already use for the one real IdentityProvider.
const identityProvider = new BackendIdentityProvider()

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) -- same
// constant PassengerLanding.tsx/RideRequest.tsx already use for
// `/v1/connections`.
//
// Product audit (2026-09-11/12): resolved via [resolveBackendBaseUrl] --
// see that function's own KDoc (`api/apiClient.ts`). This screen is PIOS's
// own designated "repeat a transaction" path (this file's own KDoc,
// docs/PIOS_PRODUCT_VISION.md §6/§8) -- deliberately left out of that first
// fix's scope (RideRequest/DriverHome/PassengerLanding only) as a named,
// out-of-scope finding; closed now that finding the next real
// complete-or-repeat gap is this task's own explicit target. A real
// returning passenger's own phone has nothing listening on
// `localhost:8082` -- this screen's connections list never loaded for one,
// same root cause, same fix.
const PASSENGER_EXPERIENCE_BASE_URL = resolveBackendBaseUrl(import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL, 'http://localhost:8082')

interface ConnectionItem {
  connectionId: string
  driverId: string
  createdAt: string
  isPrimary: boolean
}

interface DriverInfo {
  id: string
  availability: 'AVAILABLE' | 'UNAVAILABLE'
  displayName: string | null
}

type Status = 'loading' | 'empty' | 'ready' | 'error'

/**
 * "Мои водители" -- the screen this product's own vision names as its
 * central difference from an aggregator (docs/PIOS_PRODUCT_VISION.md §6/§8;
 * the product owner's own follow-up, 2026-09-05, Section 8: "Александр
 * открывает PIOS снова. Он видит: Мои водители..."). Closes a real, verified
 * gap: before this screen, a returning passenger who lost or never
 * bookmarked a specific driver's own `/i/:driverId` link had no way back to
 * that driver at all -- `GET /v1/connections?passengerReference=...`
 * (Passenger Experience, ADR-054) already tracked the relationship, but
 * nothing surfaced it. New frontend only: no backend change, this endpoint
 * and its authorization (ADR-055 Decision 4) already exist and are already
 * exercised internally by `PassengerLanding.tsx`'s own Circle-of-Trust check.
 *
 * Deliberately scoped to *returning* passengers only -- if no identity
 * exists on this device at all, this screen has nothing to show and does
 * not offer its own login form (that is `PassengerLanding`'s job, reached
 * through a specific driver's own link, not this one).
 */
export function MyDrivers() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<Status>('loading')
  const [drivers, setDrivers] = useState<Array<{ connection: ConnectionItem; driver: DriverInfo | null }>>([])

  useEffect(() => {
    let active = true
    load(active)
    return () => {
      active = false
    }
  }, [])

  function load(active: boolean) {
    setStatus('loading')
    identityProvider.restoreIdentity().then((identity) => {
      if (!active) {
        return
      }
      if (!identity) {
        setStatus('empty')
        return
      }
      request<ConnectionItem[]>(`/v1/connections?passengerReference=${identity.identityId}`, {
        headers: { Authorization: `Bearer ${identity.token}` },
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
        .then((connections) => {
          if (!active) {
            return
          }
          if (connections.length === 0) {
            setStatus('empty')
            return
          }
          Promise.all(
            connections.map((connection) =>
              // Best-effort, same tolerance DriverHome.tsx's own
              // `loadOrderDetails` already established: a driver lookup
              // failure hides that one driver's name, not the whole list.
              request<DriverInfo>(`/v1/drivers/${connection.driverId}`)
                .then((driver) => ({ connection, driver }))
                .catch(() => ({ connection, driver: null }))
            )
          ).then((results) => {
            if (!active) {
              return
            }
            // Primary first (ADR-054's own passenger-chosen fact), then
            // whatever order the backend returned the rest in -- no other
            // ranking exists to sort by, and inventing one is exactly what
            // this component's own reused `DriverTrustIndicator` already
            // avoids doing.
            results.sort((a, b) => Number(b.connection.isPrimary) - Number(a.connection.isPrimary))
            setDrivers(results)
            setStatus('ready')
          })
        })
        .catch(() => {
          if (active) {
            setStatus('error')
          }
        })
    })
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <Heading level={1}>Мои водители</Heading>

        {status === 'loading' && <LoadingState label="Загружаем ваших водителей…" />}

        {status === 'error' && (
          <ErrorState
            message="Не удалось загрузить список водителей. Проверьте связь с интернетом."
            onRetry={() => load(true)}
          />
        )}

        {status === 'empty' && (
          <Text role="body" tone="secondary">
            Пока нет сохранённых водителей. Чтобы добавить водителя сюда, закажите поездку по его персональной
            ссылке — в следующий раз вы найдёте его здесь.
          </Text>
        )}

        {status === 'ready' && (
          <div className={styles.list}>
            {drivers.map(({ connection, driver }) => {
              const isAvailable = driver?.availability === 'AVAILABLE'
              return (
                /* UI/UX redesign, Stage 4 (2026-09-12): an unavailable
                   driver used to differ from an available one only by
                   [DriverTrustIndicator]'s own small dot + caption text --
                   real, but not something a passenger scanning several
                   cards at once picks up "instantly" (this stage's own
                   explicit requirement). Muting the whole card is purely
                   presentational, scoped to this one screen (`Card` itself
                   takes no className, so the dim applies to a wrapping
                   element instead) -- the actual gate stays exactly what
                   it already was: [Button]'s own `disabled`, unchanged. */
                <div key={connection.connectionId} className={isAvailable ? undefined : styles.unavailable}>
                  <Card>
                    <DriverTrustIndicator
                      name={driver?.displayName ?? 'Водитель PIOS'}
                      availability={driver?.availability}
                      isPrimary={connection.isPrimary}
                      showAvatar
                    />
                    {/* Product audit follow-up (2026-09-12): this action used
                        to stay enabled regardless of [driver.availability],
                        unlike this exact same "order a connected driver"
                        action in RideRequest.tsx's own circle-of-trust step
                        (`disabled={member.availability !== 'AVAILABLE'}`) --
                        an inconsistency for the identical underlying choice.
                        A passenger reordering through this screen (this
                        product's own designated repeat path) could tap
                        through to /request for a driver already shown as
                        "Недоступен" just above and place an order nobody is
                        working to answer. [DriverTrustIndicator] already
                        renders that fact -- this only stops acting on it. */}
                    <Button
                      label="Заказать поездку"
                      variant="primary"
                      onClick={() => navigate(`/i/${connection.driverId}/request`)}
                      disabled={!isAvailable}
                    />
                  </Card>
                </div>
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}
