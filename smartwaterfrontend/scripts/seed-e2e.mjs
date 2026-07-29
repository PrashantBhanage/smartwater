const API = 'http://localhost:8080/api'
const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.headers ?? {}),
    },
  })

  const text = await response.text()
  const body = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${path} failed (${response.status}): ${text}`)
  }
  return body
}

async function login(email, password) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

async function register(payload) {
  return request('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

const defaultAdmin = await login('admin@smartwater.local', 'SmartWater#2024')
const defaultToken = defaultAdmin.token

const password = 'SmartWater#2026'
const today = new Date()
const isoDate = (daysAgo) => {
  const date = new Date(today)
  date.setDate(today.getDate() - daysAgo)
  return date.toISOString().slice(0, 10)
}

const fixtures = [
  {
    name: 'Green Meadows',
    prefix: 'green',
    flats: ['A-1', 'A-2'],
    address: '12 Demo Park Road',
    totalHouseholds: 2,
  },
  {
    name: 'Sunrise Residency',
    prefix: 'sunrise',
    flats: ['B-1', 'B-2'],
    address: '48 Morning Avenue',
    totalHouseholds: 2,
  },
]

const summary = {
  stamp,
  password,
  apartments: [],
  admins: [],
  residents: [],
  households: [],
  tariffPlans: [],
  billingCycles: [],
  purchases: [],
  usageLogs: [],
}

for (const fixture of fixtures) {
  const adminEmail = `${fixture.prefix}.admin.${stamp}@smartwater.local`
  const apartment = await request('/apartments', {
    method: 'POST',
    token: defaultToken,
    body: JSON.stringify({
      name: fixture.name,
      address: fixture.address,
      totalHouseholds: fixture.totalHouseholds,
      adminContact: adminEmail,
    }),
  })
  summary.apartments.push(apartment)

  const admin = await register({
    fullName: `${fixture.name} Admin`,
    email: adminEmail,
    password,
    role: 'ADMIN',
    apartmentId: apartment.id,
  })
  summary.admins.push({ email: adminEmail, password, userId: admin.userId, apartmentId: apartment.id })

  const adminSession = await login(adminEmail, password)

  const households = []
  for (const [index, flatNumber] of fixture.flats.entries()) {
    const household = await request('/households', {
      method: 'POST',
      token: adminSession.token,
      body: JSON.stringify({
        apartmentId: apartment.id,
        flatNumber,
        areaSqft: index === 0 ? 920 : 1040,
        occupancyCount: index === 0 ? 3 : 4,
        hasMeter: true,
        dailyThresholdLiters: 500,
      }),
    })
    households.push(household)
    summary.households.push(household)

    const residentEmail = `${fixture.prefix}.${flatNumber.toLowerCase().replace('-', '')}.${stamp}@smartwater.local`
    const resident = await register({
      fullName: `${fixture.name} ${flatNumber} Resident`,
      email: residentEmail,
      password,
      role: 'RESIDENT',
      householdId: household.id,
    })
    summary.residents.push({
      email: residentEmail,
      password,
      userId: resident.userId,
      householdId: household.id,
      flatNumber,
      apartmentId: apartment.id,
    })

    const volumes = [420, 650, 820, 480, 620, 790, 450]
    for (const [dayIndex, volume] of volumes.entries()) {
      const log = await request('/usage-logs', {
        method: 'POST',
        token: adminSession.token,
        body: JSON.stringify({
          householdId: household.id,
          readingDate: isoDate(6 - dayIndex),
          meterReadingValue: (100 + index * 50 + dayIndex * 0.7).toFixed(3),
          volumeUsedLiters: volume,
          source: 'MANUAL',
        }),
      })
      summary.usageLogs.push(log)
    }
  }

  const tariff = await request('/tariff-plans', {
    method: 'POST',
    token: adminSession.token,
    body: JSON.stringify({
      apartmentId: apartment.id,
      tier1LimitKl: 20,
      tier1Rate: 18,
      tier2Rate: 30,
      effectiveFromDate: isoDate(30),
    }),
  })
  summary.tariffPlans.push(tariff)

  const cycle = await request('/billing-cycles', {
    method: 'POST',
    token: adminSession.token,
    body: JSON.stringify({
      apartmentId: apartment.id,
      cycleStartDate: isoDate(6),
      cycleEndDate: isoDate(0),
    }),
  })
  summary.billingCycles.push(cycle)

  const purchase = await request('/purchases', {
    method: 'POST',
    token: adminSession.token,
    body: JSON.stringify({
      apartmentId: apartment.id,
      cycleId: cycle.id,
      volumePurchasedKl: 40,
      unitCost: 28,
      purchaseDate: isoDate(1),
      source: 'TANKER',
    }),
  })
  summary.purchases.push(purchase)

  const finalized = await request(`/billing-cycles/${cycle.id}/finalize`, {
    method: 'PATCH',
    token: adminSession.token,
    body: JSON.stringify({}),
  })
  summary.billingCycles[summary.billingCycles.length - 1] = finalized
}

console.log(JSON.stringify(summary, null, 2))
