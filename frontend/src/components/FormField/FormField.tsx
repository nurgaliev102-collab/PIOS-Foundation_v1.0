import type { ReactNode } from 'react'
import { Text } from '../Text'
import styles from './FormField.module.css'

export interface FormFieldProps {
  label: string
  htmlFor: string
  error?: string | null
  children: ReactNode
}

/**
 * PIOS design-system foundation FormField — docs/PIOS_DESIGN_SYSTEM.md
 * Section 5: "label + input + validation message, as one composed unit,
 * so no screen hand-assembles this structure independently." The error
 * message is associated with the control via `aria-describedby`
 * (docs/PIOS_DESIGN_SYSTEM.md Section 13, Errors) — the caller's own
 * `children` control must carry a matching `id={htmlFor}` and, when an
 * error is present, `aria-describedby={`${htmlFor}-error`}`.
 */
export function FormField({ label, htmlFor, error, children }: FormFieldProps) {
  return (
    <div className={styles.field}>
      <Text role="label" as="label" htmlFor={htmlFor} tone="secondary">
        {label}
      </Text>
      {children}
      {error && (
        <p id={`${htmlFor}-error`} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
