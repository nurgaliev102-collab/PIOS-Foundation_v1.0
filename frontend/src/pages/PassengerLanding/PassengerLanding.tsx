import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Header } from '../../components/Header'
import { ActionButton } from '../../components/ActionButton'
import { getInvitationByDriverCode } from './invitationSource'
import type { InvitationInfo } from './invitationSource'
import { getPassengerIdentity, savePassengerIdentity } from '../../persistence/localPassengerIdentity'
import type { PassengerIdentity } from '../../persistence/localPassengerIdentity'
import { request } from '../../api/apiClient'
import styles from './PassengerLanding.module.css'

const MAX_NAME_LENGTH = 50

// Passenger Experience's own local port (INTERFACE_CONTRACTS.md) — Sprint
// 7B (Personal Network Flow MVP): this page now also calls that module
// directly, to record that this passenger reached PIOS through this
// driver's own invitation link.
const PASSENGER_EXPERIENCE_BASE_URL = import.meta.env.VITE_PASSENGER_EXPERIENCE_BASE_URL ?? 'http://localhost:8082'

type Step = 'loading' | 'not-found' | 'invited' | 'onboarding' | 'confirmed'

const FAQ_ITEMS: Array<{ question: string; answer: string }> = [
  {
    question: 'Что такое PIOS?',
    answer:
      'PIOS помогает вам быстро связываться с вашим водителем и оформлять поездки без звонков.',
  },
  {
    question: 'Нужно ли регистрироваться каждый раз?',
    answer: 'Нет. Достаточно сделать это один раз.',
  },
  {
    question: 'Что будет, если водитель занят?',
    answer: 'PIOS поможет найти другого свободного водителя.',
  },
]

/**
 * Passenger Landing — Sprint 8 (First User Experience). Rendered at
 * `/i/:driverCode`, this is a first-time passenger's entire understanding
 * of what PIOS is, formed in one screen — per this sprint's own guiding
 * rule, it must answer three questions before any registration form
 * appears: who invited them, what they get, and what to do next.
 *
 * A returning passenger (a local identity already exists,
 * `persistence/localPassengerIdentity.ts`) never sees any of this again —
 * this page redirects straight to Ride Request instead, per this sprint's
 * own explicit "не показывать инструкцию повторно" rule.
 *
 * No internal term (Connection, Proposal, Assignment, Dispatch,
 * Passenger, Driver ID) appears in any user-facing string on this page —
 * this sprint's own explicit UX rule.
 */
export function PassengerLanding() {
  const { driverCode } = useParams<{ driverCode: string }>()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('loading')
  const [invitation, setInvitation] = useState<InvitationInfo | null>(null)
  const [identity, setIdentity] = useState<PassengerIdentity | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setStep('loading')
    getInvitationByDriverCode(driverCode ?? '').then((result) => {
      if (!active) {
        return
      }
      if (!result) {
        setStep('not-found')
        return
      }
      setInvitation(result)
      const existingIdentity = getPassengerIdentity()
      if (existingIdentity) {
        // Returning passenger: the welcome screen and its instructions
        // already did their job on a previous visit — go straight to
        // creating an order.
        navigate(`/i/${driverCode ?? ''}/request`, { replace: true })
        return
      }
      setStep('invited')
    })
    return () => {
      active = false
    }
  }, [driverCode, navigate])

  function handleContinue() {
    setStep('onboarding')
  }

  function handleNameChange(value: string) {
    setName(value)
    if (nameError) {
      setNameError(null)
    }
  }

  async function handleNameSubmit() {
    const trimmed = name.trim()
    if (!trimmed) {
      setNameError('Пожалуйста, введите имя.')
      return
    }
    if (trimmed.length > MAX_NAME_LENGTH) {
      setNameError(`Имя должно быть короче ${MAX_NAME_LENGTH} символов.`)
      return
    }
    const saved = savePassengerIdentity(trimmed)
    setIdentity(saved)
    setStep('confirmed')

    // Sprint 7B (Personal Network Flow MVP): records the connection this
    // invitation just created. Idempotent on the backend (opening the same
    // link again returns 200, not a duplicate), so no local guard against
    // calling this more than once is needed. Deliberately not blocking or
    // surfaced to the passenger on failure: this is bookkeeping for a
    // feature (Ride Request auto-proposing to this driver) the passenger
    // has not reached yet, not something their own onboarding should stall
    // on.
    try {
      await request('/v1/connections', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ driverId: driverCode ?? '', passengerReference: saved.id }),
        baseUrl: PASSENGER_EXPERIENCE_BASE_URL,
      })
    } catch {
      // Known, accepted limitation — see comment above.
    }
  }

  function handleCreateFirstOrder() {
    navigate(`/i/${driverCode ?? ''}/request`)
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        {step === 'loading' && <p className={styles.status}>Загрузка…</p>}

        {step === 'not-found' && (
          <p className={styles.status}>Ссылка недействительна или водитель ещё не зарегистрирован.</p>
        )}

        {step === 'invited' && invitation && (
          <>
            <div className={styles.heroAvatar} aria-hidden="true">
              {invitation.driverName.trim().charAt(0).toUpperCase()}
            </div>
            <p className={styles.heroCaption}>Ваш водитель — {invitation.driverName}</p>

            <h1 className={styles.title}>👋 Вас пригласил {invitation.driverName}</h1>
            <p className={styles.subtitle}>
              Теперь вы можете быстро заказывать поездки через личный профиль {invitation.driverName}.
            </p>

            <section className={styles.stepsCard}>
              <h2 className={styles.stepsTitle}>Как работает PIOS</h2>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  🚖
                </span>
                <div>
                  <p className={styles.stepTitle}>Заказывайте поездки</p>
                  <p className={styles.stepDescription}>Создавайте заказ прямо в приложении.</p>
                </div>
              </div>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  👤
                </span>
                <div>
                  <p className={styles.stepTitle}>Сначала заказ получает {invitation.driverName}</p>
                  <p className={styles.stepDescription}>Если он свободен — заказ сразу придёт ему.</p>
                </div>
              </div>

              <div className={styles.stepRow}>
                <span className={styles.stepEmoji} aria-hidden="true">
                  🔄
                </span>
                <div>
                  <p className={styles.stepTitle}>Если {invitation.driverName} занят</p>
                  <p className={styles.stepDescription}>
                    Ваш заказ не потеряется. PIOS предложит его другому свободному водителю.
                  </p>
                </div>
              </div>
            </section>

            <div className={styles.actionRow}>
              <ActionButton label="Начать" variant="primary" onClick={handleContinue} />
            </div>

            <section className={styles.faqSection}>
              {FAQ_ITEMS.map((item) => (
                <div key={item.question} className={styles.faqItem}>
                  <p className={styles.faqQuestion}>{item.question}</p>
                  <p className={styles.faqAnswer}>{item.answer}</p>
                </div>
              ))}
            </section>
          </>
        )}

        {step === 'onboarding' && (
          <>
            <h1 className={styles.question}>Как к вам обращаться?</h1>
            <input
              className={styles.input}
              type="text"
              value={name}
              maxLength={MAX_NAME_LENGTH}
              placeholder="Ваше имя"
              aria-label="Ваше имя"
              onChange={(event) => handleNameChange(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  void handleNameSubmit()
                }
              }}
            />
            {nameError && (
              <p className={styles.error} role="alert">
                {nameError}
              </p>
            )}
            <div className={styles.actionRow}>
              <ActionButton label="Продолжить" variant="primary" onClick={() => void handleNameSubmit()} />
            </div>
          </>
        )}

        {step === 'confirmed' && identity && (
          <>
            <h1 className={styles.greeting}>Добро пожаловать!</h1>
            <p className={styles.subtitle}>Вы успешно подключены к PIOS.</p>

            <section className={styles.checklist}>
              <p className={styles.checklistTitle}>Теперь вы можете:</p>
              <p className={styles.checklistItem}>✅ заказать поездку</p>
              <p className={styles.checklistItem}>✅ пользоваться личной ссылкой {invitation?.driverName}</p>
              <p className={styles.checklistItem}>✅ не искать его номер телефона</p>
            </section>

            <div className={styles.actionRow}>
              <ActionButton label="Создать первый заказ" variant="primary" onClick={handleCreateFirstOrder} />
            </div>
          </>
        )}
      </main>
    </div>
  )
}
