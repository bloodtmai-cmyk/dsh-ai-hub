import { spawn } from 'node:child_process'

const MAX_REQUEST_BYTES = 512 * 1024
const MAX_RESPONSE_BYTES = 4 * 1024 * 1024
const MAX_ERROR_BYTES = 64 * 1024
const EXPOSED_ERROR_BYTES = 2 * 1024

export const OPERATIONS = Object.freeze({
  identity: Object.freeze({
    whoami: Object.freeze({ command: ['identity', 'whoami'], acceptsJson: false }),
  }),
  contact: Object.freeze({
    search: Object.freeze({ command: ['contact', 'users', 'search'], acceptsJson: true }),
  }),
  calendar: Object.freeze({
    list: Object.freeze({ command: ['calendar', 'schedules', 'list'], acceptsJson: true }),
    search: Object.freeze({ command: ['calendar', 'schedules', 'search'], acceptsJson: true }),
    get: Object.freeze({ command: ['calendar', 'schedules', 'get'], acceptsJson: true }),
    create: Object.freeze({ command: ['calendar', 'schedules', 'create'], acceptsJson: true, writes: true }),
    update: Object.freeze({ command: ['calendar', 'schedules', 'update'], acceptsJson: true, writes: true }),
    cancel: Object.freeze({ command: ['calendar', 'schedules', 'cancel'], acceptsJson: true, writes: true }),
    free_list: Object.freeze({ command: ['calendar', 'schedules', 'free', 'list'], acceptsJson: true }),
    buildings_list: Object.freeze({ command: ['meeting', 'rooms', 'buildings', 'list'], acceptsJson: true }),
    rooms_search: Object.freeze({ command: ['meeting', 'rooms', 'search'], acceptsJson: true }),
  }),
  meeting: Object.freeze({
    list: Object.freeze({ command: ['meeting', 'list'], acceptsJson: true }),
    search: Object.freeze({ command: ['meeting', 'search'], acceptsJson: true }),
    get: Object.freeze({ command: ['meeting', 'get'], acceptsJson: true }),
    create: Object.freeze({ command: ['meeting', 'create'], acceptsJson: true, writes: true }),
    update: Object.freeze({ command: ['meeting', 'update'], acceptsJson: true, writes: true }),
    cancel: Object.freeze({ command: ['meeting', 'cancel'], acceptsJson: true, writes: true }),
    original_get: Object.freeze({ command: ['meeting', 'original', 'get'], acceptsJson: true }),
  }),
  message: Object.freeze({
    sessions_list: Object.freeze({ command: ['message', 'aibot', 'sessions', 'list'], acceptsJson: false }),
    send: Object.freeze({ command: ['message', 'aibot', 'send'], acceptsJson: true, writes: true }),
  }),
  doc: Object.freeze({
    search: Object.freeze({ command: ['doc', 'search'], acceptsJson: true }),
    get_content: Object.freeze({ command: ['doc', 'contents', 'get'], acceptsJson: true }),
    create: Object.freeze({ command: ['doc', 'create'], acceptsJson: true, writes: true }),
    append_content: Object.freeze({ command: ['doc', 'contents', 'append'], acceptsJson: true, writes: true }),
    overwrite_content: Object.freeze({ command: ['doc', 'contents', 'overwrite'], acceptsJson: true, writes: true }),
    rename: Object.freeze({ command: ['doc', 'names', 'update'], acceptsJson: true, writes: true }),
  }),
  sheet: Object.freeze({
    get: Object.freeze({ command: ['sheet', 'get'], acceptsJson: true }),
    get_range: Object.freeze({ command: ['sheet', 'ranges', 'get'], acceptsJson: true }),
    create: Object.freeze({ command: ['sheet', 'create'], acceptsJson: true, writes: true }),
    update_range: Object.freeze({ command: ['sheet', 'contents', 'update'], acceptsJson: true, writes: true }),
    append_row: Object.freeze({ command: ['sheet', 'rows', 'append'], acceptsJson: true, writes: true }),
    add_subsheet: Object.freeze({ command: ['sheet', 'subsheets', 'add'], acceptsJson: true, writes: true }),
  }),
  mail: Object.freeze({
    search: Object.freeze({ command: ['mail', 'search'], acceptsJson: true }),
    get: Object.freeze({ command: ['mail', 'get'], acceptsJson: true }),
    send: Object.freeze({ command: ['mail', 'send'], acceptsJson: true, writes: true }),
  }),
  todo: Object.freeze({
    list: Object.freeze({ command: ['todo', 'list'], acceptsJson: true }),
    get: Object.freeze({ command: ['todo', 'get'], acceptsJson: true }),
    create: Object.freeze({ command: ['todo', 'create'], acceptsJson: true, writes: true }),
    update: Object.freeze({ command: ['todo', 'update'], acceptsJson: true, writes: true }),
    finish: Object.freeze({ command: ['todo', 'finish'], acceptsJson: true, writes: true }),
  }),
  drive: Object.freeze({
    list_recent: Object.freeze({ command: ['disk', 'files', 'list'], acceptsJson: true }),
    search: Object.freeze({ command: ['disk', 'files', 'search'], acceptsJson: true }),
    get: Object.freeze({ command: ['disk', 'files', 'get'], acceptsJson: true }),
  }),
})

const FORBIDDEN_LOCAL_FILE_KEYS = new Set(['file_path', 'content_path'])

function assertNoLocalFileReferences(value, location = 'request_json') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoLocalFileReferences(item, `${location}[${index}]`))
    return
  }
  if (value === null || typeof value !== 'object') return
  for (const [key, child] of Object.entries(value)) {
    if (FORBIDDEN_LOCAL_FILE_KEYS.has(key)) {
      throw new Error(`${location}.${key} is not allowed; use an AI Hub-approved attachment flow`)
    }
    assertNoLocalFileReferences(child, `${location}.${key}`)
  }
}

export function parseRequest(requestJson, required) {
  if (!required && (requestJson === undefined || requestJson === null || requestJson.trim() === '')) return undefined
  if (typeof requestJson !== 'string' || requestJson.trim() === '') {
    throw new Error('request_json must be a non-empty JSON object')
  }
  if (Buffer.byteLength(requestJson, 'utf8') > MAX_REQUEST_BYTES) {
    throw new Error('request_json exceeds the 512 KiB plug-in limit')
  }
  let parsed
  try {
    parsed = JSON.parse(requestJson)
  } catch (error) {
    throw new Error('request_json is not valid JSON', { cause: error })
  }
  if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('request_json must contain one JSON object')
  }
  assertNoLocalFileReferences(parsed)
  return parsed
}

function localTimeZone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai'
}

function wallClockTime(value, timeZone) {
  if (typeof value === 'string' && !/^\d{10,13}$/.test(value.trim())) return value
  const numeric = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(numeric)) return value
  const milliseconds = Math.abs(numeric) >= 1_000_000_000_000 ? numeric : numeric * 1_000
  let parts
  try {
    parts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(new Date(milliseconds))
  } catch (error) {
    throw new Error(`request_json contains an invalid timezone_id: ${timeZone}`, { cause: error })
  }
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`
}

function requireString(request, key, operation, format) {
  if (typeof request[key] !== 'string' || request[key].trim() === '') {
    throw new Error(`WeCom ${operation} requires request_json.${key}${format ? ` (${format})` : ''}`)
  }
}

export function normalizeRequest(domain, operation, request, defaultTimeZone = localTimeZone()) {
  if (request === undefined) return undefined
  const normalized = structuredClone(request)
  if (domain === 'doc' && operation === 'create') {
    if (normalized.doc_name === undefined && typeof normalized.title === 'string') {
      normalized.doc_name = normalized.title
    }
    delete normalized.title
    requireString(normalized, 'doc_name', 'doc.create', 'use doc_name instead of title')
  }
  if (domain === 'calendar' && operation === 'create') {
    if (normalized.subject === undefined && typeof normalized.summary === 'string') {
      normalized.subject = normalized.summary
    }
    if (normalized.begin_time === undefined && normalized.start_time !== undefined) {
      normalized.begin_time = normalized.start_time
    }
    delete normalized.summary
    delete normalized.start_time
    const timeZone = normalized.timezone?.timezone_id || defaultTimeZone
    normalized.begin_time = wallClockTime(normalized.begin_time, timeZone)
    normalized.end_time = wallClockTime(normalized.end_time, timeZone)
    requireString(normalized, 'subject', 'calendar.create')
    requireString(normalized, 'begin_time', 'calendar.create', 'YYYY-MM-DD HH:mm:ss; do not calculate Unix timestamps in Bash')
    requireString(normalized, 'end_time', 'calendar.create', 'YYYY-MM-DD HH:mm:ss; do not calculate Unix timestamps in Bash')
  }
  return normalized
}

export function operationSpec(domain, operation) {
  const spec = OPERATIONS[domain]?.[operation]
  if (spec === undefined) throw new Error(`Unsupported WeCom ${domain} operation: ${operation}`)
  return spec
}

function decodedOutput(stdout, domain, operation) {
  const text = stdout.toString('utf8').replace(/^\uFEFF/, '').trim()
  if (text === '') throw new Error(`WeCom ${domain}.${operation} returned an empty response`)
  let payload
  try {
    payload = JSON.parse(text)
  } catch {
    return text
  }
  if (payload?.error !== null && typeof payload?.error === 'object') {
    const detail = errorDetail(stdout, Buffer.alloc(0))
    throw new Error(`WeCom ${domain}.${operation} failed${detail ? `: ${detail}` : ''}`)
  }
  if (payload !== null && typeof payload === 'object' && !Array.isArray(payload)) {
    delete payload.extra_identity_context
  }
  return JSON.stringify(payload, null, 2)
}

function errorDetail(stdout, stderr) {
  const stderrText = stderr.toString('utf8').trim()
  if (stderrText !== '') return stderrText.slice(0, EXPOSED_ERROR_BYTES)
  const stdoutText = stdout.toString('utf8').replace(/^\uFEFF/, '').trim()
  if (stdoutText === '') return ''
  try {
    const payload = JSON.parse(stdoutText)
    const officialError = payload?.error
    if (officialError !== null && typeof officialError === 'object') {
      return [officialError.type, officialError.code, officialError.message]
        .filter(value => value !== undefined && value !== null && String(value).trim() !== '')
        .map(String)
        .join(' | ')
        .slice(0, EXPOSED_ERROR_BYTES)
    }
  } catch {
    // Official CLI validation errors are normally JSON. Avoid exposing arbitrary stdout.
  }
  return ''
}

export async function assertManagedWeComSession(write, fetchImpl = fetch, environment = process.env) {
  const brokerURL = environment.DSH_DESKTOP_MCP_BROKER_URL
  const brokerSecret = environment.DSH_DESKTOP_MCP_BROKER_SECRET
  if (typeof brokerURL !== 'string' || brokerURL === ''
    || typeof brokerSecret !== 'string' || brokerSecret === '') {
    throw new Error('WeCom is available only inside the managed Harness desktop session')
  }
  const endpoint = new URL(brokerURL)
  endpoint.pathname = '/wecom/session'
  endpoint.search = write ? '?write=1' : ''
  const response = await fetchImpl(endpoint, {
    method: 'POST',
    headers: { Authorization: `Bearer ${brokerSecret}` },
  })
  if (!response.ok) {
    throw new Error(write
      ? 'WeCom write access requires a fresh QR confirmation in the enterprise market'
      : 'WeCom QR authorization is required in the enterprise market')
  }
}

export async function runWeCom(cliPath, domain, operation, requestJson, signal, authorize = assertManagedWeComSession) {
  const spec = operationSpec(domain, operation)
  const request = normalizeRequest(domain, operation, parseRequest(requestJson, spec.acceptsJson))
  await authorize(spec.writes === true)
  const args = [...spec.command]
  if (spec.acceptsJson) args.push('--json', JSON.stringify(request))

  return await new Promise((resolve, reject) => {
    const child = spawn(cliPath, args, {
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
      signal,
    })
    const stdout = []
    const stderr = []
    let stdoutBytes = 0
    let stderrBytes = 0
    let completed = false

    const fail = (error) => {
      if (completed) return
      completed = true
      child.kill()
      reject(error)
    }
    child.stdout.on('data', (chunk) => {
      stdoutBytes += chunk.length
      if (stdoutBytes > MAX_RESPONSE_BYTES) {
        fail(new Error(`WeCom ${domain}.${operation} response exceeds 4 MiB`))
        return
      }
      stdout.push(chunk)
    })
    child.stderr.on('data', (chunk) => {
      stderrBytes += chunk.length
      if (stderrBytes <= MAX_ERROR_BYTES) stderr.push(chunk)
    })
    child.on('error', (error) => fail(new Error(`WeCom ${domain}.${operation} could not start the official CLI`, { cause: error })))
    child.on('close', (code) => {
      if (completed) return
      completed = true
      if (code !== 0) {
        const detail = errorDetail(Buffer.concat(stdout), Buffer.concat(stderr))
        reject(new Error(`WeCom ${domain}.${operation} failed${detail ? `: ${detail}` : ''}`))
        return
      }
      try {
        resolve(decodedOutput(Buffer.concat(stdout), domain, operation))
      } catch (error) {
        reject(error)
      }
    })
  })
}
