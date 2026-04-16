/**
 * Scenarios:
 *   browse   — 100 VUs: GET /api/events + GET /api/events/:id/seats
 *   purchase —  50 VUs: hold → checkout
 *
 * Run:
 *   k6 run load-test.js
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'

const BASE_URL = __ENV.BASE_URL || 'http://localhost'

export const options = {
  scenarios: {

    browse: {
      executor:  'ramping-vus',
      startVUs:  0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '2m',  target: 100 },
        { duration: '15s', target: 0   },
      ],
      exec: 'browseScenario',
    },

    purchase: {
      executor:  'ramping-vus',
      startVUs:  0,
      stages: [
        { duration: '10s', target: 20 },
        { duration: '2m',  target: 20 },
        { duration: '15s', target: 0  },
      ],
      exec: 'purchaseScenario',
    },
  },

  thresholds: {
    http_req_failed: ['rate<0.05'],

    'http_req_duration{scenario:browse}':   ['p(95)<1000'],

    'http_req_duration{scenario:purchase}': ['p(95)<5000'],

    checks: ['rate>0.95'],
  },
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }
function authHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }
}

// setup() — runs once
export function setup() {
  const res = http.get(`${BASE_URL}/api/events`)
  if (res.status !== 200 || !Array.isArray(res.json()) || res.json().length === 0) {
    throw new Error(`setup: GET /api/events failed (${res.status}) — is the app running?`)
  }
  const event = res.json()[3]
  console.log(`setup: using event "${event.name}" (${event.id})`)
  return { eventId: event.id }
}

// Browse scenario
export function browseScenario(data) {
  // Request 1: list all events
  const eventsRes = http.get(`${BASE_URL}/api/events`)
  check(eventsRes, {
    'browse: events 200':      (r) => r.status === 200,
    'browse: events is array': (r) => Array.isArray(r.json()),
  })
  sleep(1)

  // Request 2: seating chart for the event
  const seatsRes = http.get(`${BASE_URL}/api/events/${data.eventId}/seats`)
  check(seatsRes, {
    'browse: seats 200':      (r) => r.status === 200,
    'browse: seats is array': (r) => Array.isArray(r.json()),
  })
  sleep(2)
}

// Purchase scenario
export function purchaseScenario(data) {
  const email = `loadtest_${uuidv4()}@test.com`
  const registerRes = http.post(
      `${BASE_URL}/api/auth/register`,
      JSON.stringify({ email, password: 'Password1!', firstName: 'Load', lastName: 'Test' }),
      { headers: JSON_HEADERS }
  )
  check(registerRes, { 'purchase: register 201': (r) => r.status === 201 })
  if (registerRes.status !== 201) { sleep(2); return }

  const token = registerRes.json('token')
  sleep(1)

  const seatsRes = http.get(`${BASE_URL}/api/events/${data.eventId}/seats`)
  if (seatsRes.status !== 200 || !Array.isArray(seatsRes.json())) { sleep(2); return }

  const available = seatsRes.json().filter(s => s.status === 'AVAILABLE')
  if (available.length === 0) {
    sleep(2)
    return
  }

  const seat = available[Math.floor(Math.random() * available.length)]
  sleep(1)

  const holdRes = http.post(
      `${BASE_URL}/api/holds`,
      JSON.stringify({ seatId: seat.id }),
      { headers: authHeaders(token) }
  )
  check(holdRes, { 'purchase: hold 201': (r) => r.status === 201 })
  if (holdRes.status !== 201) { sleep(2); return }

  const holdToken = holdRes.json('holdToken')
  sleep(1)

  const checkoutRes = http.post(
      `${BASE_URL}/api/orders/checkout`,
      JSON.stringify({ holdToken, paymentProvider: 'STRIPE', paymentToken: 'tok_visa' }),
      { headers: authHeaders(token) }
  )
  check(checkoutRes, {
    'purchase: checkout 2xx':    (r) => r.status === 201 || r.status === 200,
    'purchase: order confirmed':  (r) => r.json('status') === 'CONFIRMED',
  })
  sleep(2)
}
