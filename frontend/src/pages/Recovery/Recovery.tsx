import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Header } from '../../components/Header'
import { Heading } from '../../components/Heading'
import { Text } from '../../components/Text'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Input } from '../../components/Input'
import { PasswordInput } from '../../components/PasswordInput'
import { StatusMessage } from '../../components/StatusMessage'
import { BackendIdentityProvider } from '../../identity/BackendIdentityProvider'
import { normalizePhone, isValidPhone, PHONE_FORMAT_HINT } from '../../identity/phoneFormat'
import styles from './Recovery.module.css'

// ADR-038/ADR-039/ADR-055/ADR-082: same singleton pattern every other
// identity-facing screen (DriverHome, PassengerLanding, MyDrivers) already
// uses for the one real IdentityProvider.
const identityProvider = new BackendIdentityProvider()

const GENERIC_REQUEST_MESSAGE =
  'Если этот номер зарегистрирован и телефон подтверждён, мы отправили код подтверждения по SMS. Проверьте телефон.'

type Step = 'request' | 'confirm' | 'done'

/**
 * ADR-082 (D-03) — "Забыли пароль?": phone-verified identity recovery for
 * a registered account whose phone has already been confirmed (D-03.1,
 * D-03.2). A standalone route (`/recovery`), not folded into
 * `DriverHome.tsx`'s own already-large auth screen, so this genuinely new
 * entry point does not require touching that screen's own login/register
 * flow beyond the one link that opens this page (D-03: "implement only
 * what ADR-082 requires; do not redesign unrelated UI").
 *
 * Both steps below are deliberately vague about *why* anything failed —
 * mirroring the backend's own D-03.7 "never distinguish" discipline: the
 * request step shows the exact same message whether the phone is unknown,
 * unregistered, unverified, or genuinely eligible (no account enumeration,
 * carried through to this layer, not just the API response); the confirm
 * step shows the same generic error for a wrong code, an expired one,
 * exhausted attempts, or an unknown phone.
 */
export function Recovery() {
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('request')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [phoneError, setPhoneError] = useState<string | null>(null)
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [isSubmittingRequest, setIsSubmittingRequest] = useState(false)
  const [isSubmittingConfirm, setIsSubmittingConfirm] = useState(false)

  async function handleRequestSubmit() {
    const normalized = normalizePhone(phone)
    if (!isValidPhone(normalized)) {
      setPhoneError(PHONE_FORMAT_HINT)
      return
    }
    setPhoneError(null)
    setIsSubmittingRequest(true)
    try {
      // ADR-082 D-03.7: this resolves the same way regardless of what the
      // backend actually did -- there is nothing to branch on here by
      // design, which is exactly what makes the UI itself enumeration-safe
      // rather than merely relying on the backend's own generic response.
      await identityProvider.requestRecovery(normalized)
    } catch {
      // A network/transport failure still moves to the confirm step with
      // the same generic message -- a caller who cannot reach the network
      // at all will simply see the code never arrive, not a different,
      // more informative error that would itself leak something.
    } finally {
      setIsSubmittingRequest(false)
      setPhone(normalized)
      setStep('confirm')
    }
  }

  async function handleConfirmSubmit() {
    if (newPassword.length < 10) {
      setConfirmError('Пароль должен содержать не менее 10 символов.')
      return
    }
    if (newPassword !== confirmPassword) {
      setConfirmError('Пароли не совпадают.')
      return
    }
    if (!code.trim()) {
      setConfirmError('Введите код из SMS.')
      return
    }
    setConfirmError(null)
    setIsSubmittingConfirm(true)
    try {
      await identityProvider.confirmRecovery(phone, code.trim(), newPassword)
      setStep('done')
    } catch {
      // Never distinguishes wrong code / expired / exhausted attempts /
      // unknown phone -- ADR-082 D-03.7's own discipline, carried through.
      setConfirmError('Код неверен, устарел или уже использован. Запросите новый код.')
    } finally {
      setIsSubmittingConfirm(false)
    }
  }

  return (
    <div className={styles.screen}>
      <Header />
      <main className={styles.content}>
        <Heading level={1} visual="display">
          Восстановление доступа
        </Heading>

        {step === 'request' && (
          <>
            <Text role="body" tone="secondary">
              Введите номер телефона, привязанный к вашему аккаунту PIOS. Восстановление доступно только для аккаунтов
              с подтверждённым номером телефона.
            </Text>
            <FormField label="Номер телефона" htmlFor="recovery-phone" error={phoneError}>
              <Input
                id="recovery-phone"
                type="tel"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                aria-describedby={phoneError ? 'recovery-phone-error' : undefined}
              />
            </FormField>
            <div className={styles.actionRow}>
              <Button label="Отправить код" variant="primary" loading={isSubmittingRequest} onClick={() => void handleRequestSubmit()} />
            </div>
          </>
        )}

        {step === 'confirm' && (
          <>
            <StatusMessage tone="information">{GENERIC_REQUEST_MESSAGE}</StatusMessage>
            <FormField label="Код из SMS" htmlFor="recovery-code">
              <Input
                id="recovery-code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
            </FormField>
            <FormField label="Новый пароль" htmlFor="recovery-new-password">
              <PasswordInput
                id="recovery-new-password"
                className={styles.passwordControl}
                value={newPassword}
                onChange={setNewPassword}
                autoComplete="new-password"
              />
            </FormField>
            <FormField label="Повторите новый пароль" htmlFor="recovery-confirm-password">
              <PasswordInput
                id="recovery-confirm-password"
                className={styles.passwordControl}
                value={confirmPassword}
                onChange={setConfirmPassword}
                autoComplete="new-password"
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    void handleConfirmSubmit()
                  }
                }}
              />
            </FormField>
            {confirmError && (
              <p className={styles.error} role="alert">
                {confirmError}
              </p>
            )}
            <div className={styles.actionRow}>
              <Button
                label="Восстановить доступ"
                variant="primary"
                loading={isSubmittingConfirm}
                onClick={() => void handleConfirmSubmit()}
              />
            </div>
            <button type="button" className={styles.linkAction} onClick={() => setStep('request')}>
              Запросить код ещё раз
            </button>
          </>
        )}

        {step === 'done' && (
          <>
            {/*
              ADR-082 §8: deliberately does NOT claim every device/session
              is now signed out -- that is true only within `identity`'s
              own endpoints (this device's own token, just re-minted, and
              the account settings themselves), not in every other module
              until a token's natural expiry. Recommending re-login on
              other devices is honest; asserting they were "logged out" is
              not (D-03: "Do NOT falsely claim global instant logout").
            */}
            <StatusMessage tone="success">
              Пароль изменён. На этом устройстве вы уже вошли с новым паролем. Если вы входили на других устройствах,
              войдите там заново с новым паролем.
            </StatusMessage>
            <div className={styles.actionRow}>
              <Button label="На главную" variant="primary" onClick={() => navigate('/')} />
            </div>
          </>
        )}
      </main>
    </div>
  )
}
