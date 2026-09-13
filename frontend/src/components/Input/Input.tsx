import type { InputHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import styles from './Input.module.css'

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'className'> {
  invalid?: boolean
}

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'> {
  invalid?: boolean
}

export interface TextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'> {
  invalid?: boolean
}

/**
 * PIOS design-system foundation Input — docs/PIOS_DESIGN_SYSTEM.md
 * Section 5. A styled primitive only; `FormField` owns the label/error
 * composition around it, per that document's own component contract.
 */
export function Input({ invalid, ...rest }: InputProps) {
  return <input className={`${styles.control}${invalid ? ` ${styles.invalid}` : ''}`} aria-invalid={invalid || undefined} {...rest} />
}

/** Same styled-primitive contract as {@link Input}, for the one `<select>` this screen needs. */
export function Select({ invalid, children, ...rest }: SelectProps) {
  return (
    <select className={`${styles.control}${invalid ? ` ${styles.invalid}` : ''}`} aria-invalid={invalid || undefined} {...rest}>
      {children}
    </select>
  )
}

/**
 * Same styled-primitive contract as {@link Input}, for genuinely
 * multi-line free text (Product Cycle: Passenger Ride Requirements --
 * "Пожелания к поездке"). Reuses `.control`'s own base styling exactly
 * (padding, border, focus ring, touch target) with only the multi-line-
 * specific overrides (`.textarea`) layered on -- no parallel input style
 * to keep in sync with `Input`/`Select`.
 */
export function Textarea({ invalid, ...rest }: TextareaProps) {
  return (
    <textarea
      className={`${styles.control} ${styles.textarea}${invalid ? ` ${styles.invalid}` : ''}`}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  )
}
