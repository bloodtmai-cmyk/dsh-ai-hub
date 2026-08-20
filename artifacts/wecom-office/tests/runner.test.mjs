import assert from 'node:assert/strict'
import { chmod, mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import {
  assertManagedWeComSession,
  normalizeRequest,
  operationSpec,
  parseRequest,
  runWeCom,
} from '../runner.mjs'

test('maps calendar create to the fixed official command', () => {
  assert.deepEqual(operationSpec('calendar', 'create').command, ['calendar', 'schedules', 'create'])
  assert.equal(operationSpec('calendar', 'create').writes, true)
})

test('rejects non-object and unsupported requests', () => {
  assert.throws(() => parseRequest('[1]', true), /JSON object/)
  assert.throws(() => operationSpec('calendar', 'shell'), /Unsupported/)
})

test('rejects nested local file paths from model-provided JSON', () => {
  assert.throws(
    () => parseRequest('{"attachments":[{"file_path":"/etc/passwd"}]}', true),
    /not allowed/,
  )
})

test('maps document and work operations to fixed official commands', () => {
  assert.deepEqual(operationSpec('doc', 'get_content').command, ['doc', 'contents', 'get'])
  assert.deepEqual(operationSpec('sheet', 'get_range').command, ['sheet', 'ranges', 'get'])
  assert.deepEqual(operationSpec('drive', 'search').command, ['disk', 'files', 'search'])
  assert.equal(operationSpec('mail', 'send').writes, true)
})

test('normalizes common document and calendar aliases before calling the CLI', () => {
  assert.deepEqual(normalizeRequest('doc', 'create', {
    title: '文档测试',
    content: '正文',
  }), {
    doc_name: '文档测试',
    content: '正文',
  })
  assert.deepEqual(normalizeRequest('calendar', 'create', {
    summary: '晨会',
    start_time: 1787185800,
    end_time: 1787187600,
  }, 'Asia/Shanghai'), {
    subject: '晨会',
    begin_time: '2026-08-20 08:30:00',
    end_time: '2026-08-20 09:00:00',
  })
})

test('rejects incomplete create requests with the official field names', () => {
  assert.throws(
    () => normalizeRequest('doc', 'create', { content: '正文' }),
    /doc_name/,
  )
  assert.throws(
    () => normalizeRequest('calendar', 'create', { subject: '晨会' }),
    /begin_time/,
  )
})

test('passes only the fixed command and compact JSON to the official CLI', async () => {
  const root = await mkdtemp(join(tmpdir(), 'wecom-office-'))
  const cli = join(root, 'wecom-cli')
  await writeFile(cli, `#!/usr/bin/env node
console.log(JSON.stringify({ args: process.argv.slice(2) }))
`)
  await chmod(cli, 0o755)

  const request = '{"subject":"晨会","begin_time":"2026-08-20 08:30:00","end_time":"2026-08-20 09:00:00"}'
  const output = JSON.parse(await runWeCom(cli, 'calendar', 'create', request, undefined, async (write) => {
    assert.equal(write, true)
  }))
  assert.deepEqual(output.args, ['calendar', 'schedules', 'create', '--json', request])
})

test('keeps official CLI errors actionable and removes private identity context', async () => {
  const root = await mkdtemp(join(tmpdir(), 'wecom-office-output-'))
  const failingCli = join(root, 'wecom-cli-fail')
  await writeFile(failingCli, `#!/usr/bin/env node
console.log(JSON.stringify({ error: { type: 'ValidationError', code: 400, message: 'doc_name is required' } }))
`)
  await chmod(failingCli, 0o755)
  await assert.rejects(
    runWeCom(failingCli, 'doc', 'create', '{"doc_name":"测试"}', undefined, async () => {}),
    /ValidationError \| 400 \| doc_name is required/,
  )

  const successfulCli = join(root, 'wecom-cli-ok')
  await writeFile(successfulCli, `#!/usr/bin/env node
console.log(JSON.stringify({ extra_identity_context: '<private>', schedule_id: 'schedule-1' }))
`)
  await chmod(successfulCli, 0o755)
  const output = JSON.parse(await runWeCom(successfulCli, 'calendar', 'create', JSON.stringify({
    subject: '晨会',
    begin_time: '2026-08-20 08:30:00',
    end_time: '2026-08-20 09:00:00',
  }), undefined, async () => {}))
  assert.deepEqual(output, { schedule_id: 'schedule-1' })
})

test('requires the Harness broker lease and distinguishes write operations', async () => {
  let requested
  await assertManagedWeComSession(true, async (url, init) => {
    requested = { url: String(url), init }
    return { ok: true }
  }, {
    DSH_DESKTOP_MCP_BROKER_URL: 'http://127.0.0.1:39001/token',
    DSH_DESKTOP_MCP_BROKER_SECRET: 'session-secret',
  })
  assert.equal(requested.url, 'http://127.0.0.1:39001/wecom/session?write=1')
  assert.equal(requested.init.headers.Authorization, 'Bearer session-secret')

  await assert.rejects(
    assertManagedWeComSession(false, async () => ({ ok: false }), {
      DSH_DESKTOP_MCP_BROKER_URL: 'http://127.0.0.1:39001/token',
      DSH_DESKTOP_MCP_BROKER_SECRET: 'session-secret',
    }),
    /QR authorization is required/,
  )
})
