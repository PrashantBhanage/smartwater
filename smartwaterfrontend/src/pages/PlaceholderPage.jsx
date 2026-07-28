/**
 * Placeholder until visual direction is approved on Login.
 * Keeps shell/nav structure ready for the next build pass.
 */
export default function PlaceholderPage({ title, note }) {
  return (
    <div>
      <h1 className="sw-page-title">{title}</h1>
      <p className="sw-page-subtitle">{note}</p>
      <div
        className="sw-glass"
        style={{
          marginTop: 28,
          padding: 28,
          borderRadius: 'var(--sw-radius)',
          color: 'var(--sw-text-secondary)',
          fontSize: 'var(--sw-fs-sm)',
        }}
      >
        Placeholder — wiring this screen to live APIs next, once Login direction is confirmed.
      </div>
    </div>
  )
}
