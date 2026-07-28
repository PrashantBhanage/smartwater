import PlaceholderPage from './PlaceholderPage'

export function AdminOverviewPage() {
  return (
    <PlaceholderPage
      title="Overview"
      note="Household status, open cycle, and recent alerts will land here."
    />
  )
}

export function AdminHouseholdsPage() {
  return (
    <PlaceholderPage
      title="Households"
      note="List, add household, assign resident, meter config."
    />
  )
}

export function AdminBillingPage() {
  return (
    <PlaceholderPage
      title="Billing"
      note="Tariff plans, purchases, open / finalize / archive cycles."
    />
  )
}

export function AdminUploadsPage() {
  return (
    <PlaceholderPage
      title="CSV Upload"
      note="Bulk usage upload with inserted / skipped / failed summary."
    />
  )
}

export function ResidentDashboardPage() {
  return (
    <PlaceholderPage
      title="Dashboard"
      note="Usage chart, current cycle summary, recent invoices."
    />
  )
}

export function ResidentInvoicesPage() {
  return (
    <PlaceholderPage title="Invoices" note="Invoice history for your household." />
  )
}

export function ResidentAlertsPage() {
  return (
    <PlaceholderPage title="Alerts" note="Threshold and leak alerts for your flat." />
  )
}
