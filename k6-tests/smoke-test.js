/**
 * smoke-test.js — Happy-path
 *
 * Run:
 *   k6 run smoke-test.js
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js'

const BASE_URL = __ENV.BASE_URL || 'http://localhost'

export const options = {
  vus:      1,
  duration: '1m',
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<3000'],
    checks:            ['rate>0.99'],
  },
}

const JSON_HEADERS = { 'Content-Type': 'application/json' }

function authHeaders(token) {
  return { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` }
}

export default function () {
  // 1. Register
  const email = `smoketest_${uuidv4()}@test.com`
  const registerRes = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ email, password: 'Password1!', firstName: 'Smoke', lastName: 'Test' }),
    { headers: JSON_HEADERS }
  )
  check(registerRes, { 'register 201': (r) => r.status === 201 })
  if (registerRes.status !== 201) {
    console.error('Register failed:', registerRes.status, registerRes.body)
    return
  }
  const token = registerRes.json('token')

  const eventsRes = http.get(`${BASE_URL}/api/events`)
  check(eventsRes, {
    'events 200':       (r) => r.status === 200,
    'events is array':  (r) => Array.isArray(r.json()),
    'at least 1 event': (r) => Array.isArray(r.json()) && r.json().length > 0,
  })
  if (eventsRes.status !== 200 || !Array.isArray(eventsRes.json()) || eventsRes.json().length === 0) {
    console.error('No events found')
    return
  }

  const event = eventsRes.json()[0]
  console.log(`Using event: ${event.name} (${event.id})`)
  sleep(1)

  const seatsRes = http.get(`${BASE_URL}/api/events/${event.id}/seats`)
  check(seatsRes, {
    'seats 200':      (r) => r.status === 200,
    'seats is array': (r) => Array.isArray(r.json()),
    'has seats':      (r) => Array.isArray(r.json()) && r.json().length > 0,
  })
  if (seatsRes.status !== 200 || !Array.isArray(seatsRes.json())) {
    console.error('Seats failed:', seatsRes.status, seatsRes.body)
    return
  }

  const availableSeat = seatsRes.json().find(s => s.status === 'AVAILABLE')
  if (!availableSeat) {
    console.warn('No available seats — all sold out')
    return
  }
  sleep(1)

  const holdRes = http.post(
    `${BASE_URL}/api/holds`,
    JSON.stringify({ seatId: availableSeat.id }),
    { headers: authHeaders(token) }
  )
  check(holdRes, { 'hold 201': (r) => r.status === 201 })
  if (holdRes.status !== 201) {
    console.error('Hold failed:', holdRes.status, holdRes.body)
    return
  }
  const holdToken = holdRes.json('holdToken')
  sleep(1)

  const checkoutRes = http.post(
    `${BASE_URL}/api/orders/checkout`,
    JSON.stringify({ holdToken, paymentProvider: 'STRIPE', paymentToken: 'tok_visa' }),
    { headers: authHeaders(token) }
  )
  check(checkoutRes, {
    'checkout 2xx':    (r) => r.status === 201 || r.status === 200,
    'order confirmed': (r) => r.json('status') === 'CONFIRMED',
  })
  if (checkoutRes.status !== 201 && checkoutRes.status !== 200) {
    console.error('Checkout failed:', checkoutRes.status, checkoutRes.body)
    return
  }

  const ordersRes = http.get(`${BASE_URL}/api/orders/me`, { headers: authHeaders(token) })
  check(ordersRes, {
    'orders 200':      (r) => r.status === 200,
    'has order':       (r) => Array.isArray(r.json()) && r.json().length > 0,
    'order confirmed': (r) => Array.isArray(r.json()) && r.json().some(o => o.status === 'CONFIRMED'),
  })

  sleep(1)
}
