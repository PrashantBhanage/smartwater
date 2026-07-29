import { writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const baseUrl = 'http://127.0.0.1:5173'
const runStamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const password = 'SmartWater#2026'

const seeded = {
  apartmentId: '5',
  householdId: '5',
  adminEmail: 'green.admin.20260729072130@smartwater.local',
  residentEmail: 'green.a1.20260729072130@smartwater.local',
}

const newResident = {
  fullName: `Demo Resident ${runStamp}`,
  email: `demo.resident.${runStamp}@smartwater.local`,
  password,
}

const consoleIssues = []

function recordIssue(kind, page, detail) {
  consoleIssues.push({ kind, url: page.url(), detail })
}

async function expectVisible(page, text) {
  await page.getByText(text, { exact: false }).first().waitFor({ state: 'visible', timeout: 10_000 })
}

async function login(page, email, pwd) {
  await page.goto(`${baseUrl}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(pwd)
  await page.getByRole('button', { name: /^Sign in$/ }).click()
}

async function signOut(page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.waitForURL('**/login', { timeout: 10_000 })
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()

page.on('console', (message) => {
  if (message.type() === 'error' || message.type() === 'warning') {
    recordIssue(`console.${message.type()}`, page, message.text())
  }
})
page.on('pageerror', (error) => recordIssue('pageerror', page, error.stack || error.message))

try {
  await page.goto(`${baseUrl}/register`)
  await expectVisible(page, 'Join SmartWater')
  await page.getByLabel('Full Name').fill(newResident.fullName)
  await page.getByLabel('Email').fill(newResident.email)
  await page.getByLabel(/Password/).fill(newResident.password)
  await page.getByLabel('Apartment Complex').selectOption(seeded.apartmentId)
  await page.getByLabel('Flat Number').selectOption(seeded.householdId)
  await page.getByRole('button', { name: /Create resident account/ }).click()
  await page.waitForURL('**/resident', { timeout: 10_000 })
  await expectVisible(page, 'Water Usage History')
  await expectVisible(page, 'GREEN')
  await expectVisible(page, 'YELLOW')
  await expectVisible(page, 'RED')

  await signOut(page)
  await login(page, newResident.email, newResident.password)
  await page.waitForURL('**/resident', { timeout: 10_000 })
  await expectVisible(page, newResident.fullName)
  await expectVisible(page, 'A-1')
  await expectVisible(page, 'GREEN')
  await expectVisible(page, 'YELLOW')
  await expectVisible(page, 'RED')

  await page.getByRole('link', { name: 'Invoices' }).click()
  await expectVisible(page, 'Invoices')
  await expectVisible(page, 'ISSUED')
  await expectVisible(page, 'A-1')

  await page.getByRole('link', { name: 'Alerts' }).click()
  await expectVisible(page, 'Alerts')
  await expectVisible(page, 'THRESHOLD_EXCEEDED')

  await signOut(page)
  await login(page, seeded.adminEmail, password)
  await page.waitForURL('**/admin', { timeout: 10_000 })
  await expectVisible(page, 'Overview')
  await expectVisible(page, 'Households')
  await expectVisible(page, 'Billing Cycles')
  await expectVisible(page, 'Tariff Plans')

  await page.getByRole('link', { name: 'Households' }).click()
  await expectVisible(page, 'Manage Households')
  await expectVisible(page, 'A-1')
  await expectVisible(page, 'A-2')

  await page.getByRole('link', { name: 'Billing' }).click()
  await expectVisible(page, 'Tariff Config')
  await expectVisible(page, 'Current Tariffs')
  await expectVisible(page, 'Cycle Ledger')
  await expectVisible(page, 'Recorded Purchases')
  await expectVisible(page, 'FINALIZED')
  await expectVisible(page, 'TANKER')

  const csvPath = `/tmp/smartwater-upload-${runStamp}.csv`
  writeFileSync(
    csvPath,
    [
      'household_id,reading_date,meter_reading_value,volume_used_liters',
      '6,2026-07-22,149.500,430',
    ].join('\n'),
  )
  await page.getByRole('link', { name: 'CSV Upload' }).click()
  await expectVisible(page, 'CSV Upload')
  await page.setInputFiles('input[type="file"]', csvPath)
  await page.getByRole('button', { name: 'Upload CSV' }).click()
  await expectVisible(page, 'Upload Summary')
  await expectVisible(page, 'Rows Processed')

  if (consoleIssues.length > 0) {
    throw new Error(`Browser console issues:\n${JSON.stringify(consoleIssues, null, 2)}`)
  }

  console.log(JSON.stringify({
    ok: true,
    newResident,
    checked: [
      'register',
      'resident dashboard after registration',
      'resident login',
      'resident invoices',
      'resident alerts',
      'admin overview',
      'admin households',
      'admin billing/tariffs/purchases/cycles',
      'admin CSV upload',
    ],
  }, null, 2))
} catch (error) {
  console.error(JSON.stringify({
    error: error.message,
    url: page.url(),
    bodyText: (await page.locator('body').innerText()).slice(0, 4000),
    consoleIssues,
  }, null, 2))
  throw error
} finally {
  await browser.close()
}
