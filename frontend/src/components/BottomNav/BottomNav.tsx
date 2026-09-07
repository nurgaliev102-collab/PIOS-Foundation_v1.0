import type { ReactNode } from 'react'
import styles from './BottomNav.module.css'

export type MainTab = 'home' | 'work' | 'business' | 'profile'

export interface BottomNavProps {
  activeTab: MainTab
  onChange: (tab: MainTab) => void
  /** Count shown as a badge on "Работа" -- open/active orders needing a driver's response. 0 or omitted shows nothing. */
  workBadgeCount?: number
}

const TABS: Array<{ id: MainTab; label: string }> = [
  { id: 'home', label: 'Главное' },
  { id: 'work', label: 'Работа' },
  { id: 'business', label: 'Бизнес' },
  { id: 'profile', label: 'Профиль' },
]

const ICONS: Record<MainTab, ReactNode> = {
  home: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 11.5 12 4l8 7.5" />
      <path d="M6 10v9a1 1 0 0 0 1 1h4v-6h2v6h4a1 1 0 0 0 1-1v-9" />
    </svg>
  ),
  work: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3.5" y="7.5" width="17" height="12" rx="2" />
      <path d="M8.5 7.5V6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v1.5" />
      <path d="M3.5 12.5h17" />
    </svg>
  ),
  business: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 19.5V11" />
      <path d="M12 19.5V6.5" />
      <path d="M19 19.5v-6" />
    </svg>
  ),
  profile: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="8.5" r="3.25" />
      <path d="M5 20c1-3.5 4-5.5 7-5.5s6 2 7 5.5" />
    </svg>
  ),
}

/**
 * App-shell bottom navigation (product owner request, 2026-09-07) --
 * Главное/Работа/Бизнес/Профиль, matching the concept mockup's own bottom
 * bar. Fixed to the viewport bottom; callers must give their scrollable
 * content bottom padding to match (see `.content` + `--pios-bottom-nav-height`
 * in the page that renders this).
 */
export function BottomNav({ activeTab, onChange, workBadgeCount }: BottomNavProps) {
  return (
    <nav className={styles.nav} role="tablist" aria-label="Разделы приложения">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={activeTab === tab.id}
          className={`${styles.tab} ${activeTab === tab.id ? styles.tabActive : ''}`}
          onClick={() => onChange(tab.id)}
        >
          <span className={styles.icon} aria-hidden="true">
            {ICONS[tab.id]}
            {tab.id === 'work' && Boolean(workBadgeCount) && <span className={styles.badge}>{workBadgeCount}</span>}
          </span>
          <span className={styles.label}>{tab.label}</span>
        </button>
      ))}
    </nav>
  )
}
