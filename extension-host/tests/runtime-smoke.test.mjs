import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import test from 'node:test'

const bundle = fileURLToPath(new URL('../dist/ai-hub-extension-host.mjs', import.meta.url))
const apiKey = 'runtime-smoke-key-not-a-real-secret'

async function bind(port) {
  const server = createServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(port, '127.0.0.1', resolve)
  })
  return server
}

async function close(server) {
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
}

async function bounded(promise, milliseconds, message) {
  let timer
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => { timer = setTimeout(() => reject(new Error(message)), milliseconds) }),
    ])
  } finally {
    clearTimeout(timer)
  }
}

for (const signal of ['SIGTERM', 'SIGINT']) {
  test(`built host serves authenticated HTTP and releases its port on ${signal}`, { timeout: 20000 }, async t => {
    t.diagnostic(`Runtime: ${process.version}`)
    const reservation = await bind(0)
    const { port } = reservation.address()
    await close(reservation)

    const child = spawn(process.execPath, [bundle], {
      env: {
        ...process.env,
        AI_HUB_EXTENSION_HOST_BIND_ADDRESS: '127.0.0.1',
        AI_HUB_EXTENSION_HOST_PORT: String(port),
        AI_HUB_EXTENSION_HOST_API_KEY: apiKey,
        AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL: '',
        AI_HUB_JWT_ISSUER: '',
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    let output = ''
    let spawnError
    for (const stream of [child.stdout, child.stderr]) {
      stream.on('data', chunk => { output = (output + chunk.toString()).slice(-8192) })
    }
    const exited = new Promise(resolve => {
      child.once('exit', (code, exitSignal) => resolve({ code, signal: exitSignal }))
      child.once('error', error => {
        spawnError = error
        resolve({ error })
      })
    })
    t.after(async () => {
      if (child.exitCode === null && child.signalCode === null && spawnError === undefined) {
        child.kill('SIGKILL')
        await bounded(exited, 3000, 'Unable to clean up runtime smoke process')
      }
    })

    const url = `http://127.0.0.1:${port}`
    const request = (path, options = {}) => fetch(url + path, {
      ...options,
      signal: AbortSignal.timeout(3000),
    })
    const deadline = Date.now() + 8000
    let healthy = false
    while (Date.now() < deadline) {
      if (spawnError !== undefined) throw spawnError
      assert.equal(child.exitCode, null, `Host exited before readiness: ${output}`)
      assert.equal(child.signalCode, null, `Host terminated before readiness: ${output}`)
      try {
        const response = await request('/health')
        const body = await response.json()
        if (response.status === 200 && body.status === 'UP') {
          healthy = true
          break
        }
      } catch {}
      await delay(50)
    }
    assert.ok(healthy, `Host did not become ready: ${output}`)

    for (const authorization of [undefined, 'Bearer short', `Bearer ${'x'.repeat(apiKey.length)}`]) {
      const response = await request('/internal/plugins', {
        headers: authorization === undefined ? {} : { authorization },
      })
      assert.equal(response.status, 401)
      assert.equal((await response.json()).code, 'UNAUTHORIZED')
    }

    const headers = { authorization: `Bearer ${apiKey}`, 'content-type': 'application/json' }
    const inventory = await request('/internal/plugins', { headers })
    assert.equal(inventory.status, 200)
    assert.deepEqual((await inventory.json()).plugins.map(plugin => plugin.id), [
      'conversation-audit', 'gateway-catalog', 'manual-key-maintenance', 'skill-artifact', 'workbuddy-ldap-auth',
    ])

    const normalized = await request('/internal/plugins/gateway-catalog/operations/normalize', {
      method: 'POST', headers,
      body: JSON.stringify({ capabilities: [{ type: 'mcp', externalRef: 'runtime-test', name: 'Runtime test', releaseVersion: '1' }] }),
    })
    assert.equal(normalized.status, 200)
    assert.equal((await normalized.json()).capabilities[0].type, 'MCP')

    const invalid = await request('/internal/plugins/gateway-catalog/operations/normalize', {
      method: 'POST', headers, body: '{',
    })
    assert.equal(invalid.status, 400)
    assert.equal((await invalid.json()).code, 'INVALID_JSON')

    const missing = await request('/internal/plugins/missing-plugin/operations/run', {
      method: 'POST', headers, body: '{}',
    })
    assert.equal(missing.status, 404)
    assert.equal((await missing.json()).code, 'PLUGIN_NOT_FOUND')

    assert.ok(child.kill(signal))
    assert.deepEqual(await bounded(exited, 5000, `Host failed to exit on ${signal}: ${output}`), {
      code: 0, signal: null,
    })
    const rebound = await bind(port)
    await close(rebound)
  })
}
