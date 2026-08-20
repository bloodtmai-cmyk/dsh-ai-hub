import { defineTool } from '@deepseek-ai/dsh-tools'
import { execFile } from 'node:child_process'

export const name = 'agent-reach-managed'
export const inject = ['tools', 'systemPrompt']

const SEARCH_MCP = 'https://mcp.exa.ai/mcp'
const READER_BASE = 'https://r.jina.ai/'
const MAX_OUTPUT_CHARS = 60_000

function bounded(text) {
  return text.length <= MAX_OUTPUT_CHARS
    ? text
    : `${text.slice(0, MAX_OUTPUT_CHARS)}\n\n[truncated by Agent Reach]`
}

function publicTarget(value) {
  const target = new URL(value)
  if (!['http:', 'https:'].includes(target.protocol)) throw new Error('Agent Reach only reads HTTP(S) URLs')
  const host = target.hostname.toLowerCase()
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local')
    || /^127\./.test(host) || /^10\./.test(host) || /^192\.168\./.test(host)
    || /^169\.254\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host)
    || host === '::1' || host.startsWith('fc') || host.startsWith('fd') || host.startsWith('fe80:')) {
    throw new Error('Agent Reach refuses local or private network targets')
  }
  return target.toString()
}

async function runCurl(args, signal, timeoutMs) {
  return await new Promise((resolve, reject) => {
    execFile('curl', [
      '--silent', '--show-error', '--fail', '--location', '--proto', '=https',
      '--retry', '2', '--max-time', String(Math.ceil(timeoutMs / 1_000)),
      '--user-agent', 'DSH-Harness-Agent-Reach/1.5',
      ...args,
    ], {
      encoding: 'utf8',
      maxBuffer: MAX_OUTPUT_CHARS * 4,
      signal,
      timeout: timeoutMs,
      windowsHide: true,
    }, (error, stdout) => {
      if (error) reject(new Error(`Agent Reach public-web request failed: ${error.message}`, { cause: error }))
      else resolve(stdout)
    })
  })
}

async function readText(url, signal, timeoutMs = 45_000) {
  return bounded(await runCurl([
    '--header', 'Accept: text/markdown, text/plain;q=0.9',
    url,
  ], signal, timeoutMs))
}

async function searchText(query, signal, timeoutMs = 45_000) {
  const body = JSON.stringify({
    jsonrpc: '2.0',
    id: 1,
    method: 'tools/call',
    params: { name: 'web_search_exa', arguments: { query, numResults: 5, type: 'auto' } },
  })
  const response = await runCurl([
    '--header', 'Accept: application/json, text/event-stream',
    '--header', 'Content-Type: application/json',
    '--data-binary', body,
    SEARCH_MCP,
  ], signal, timeoutMs)
  const payloads = response.split(/\r?\n/)
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trim())
  const raw = payloads.at(-1) ?? response.trim()
  let message
  try {
    message = JSON.parse(raw)
  } catch (error) {
    throw new Error('Agent Reach search returned an invalid MCP response', { cause: error })
  }
  if (message.error) throw new Error(`Agent Reach search failed: ${message.error.message ?? 'unknown MCP error'}`)
  const blocks = message.result?.content
  if (!Array.isArray(blocks)) throw new Error('Agent Reach search returned no content')
  const text = blocks.filter(block => block?.type === 'text' && typeof block.text === 'string')
    .map(block => block.text).join('\n\n')
  if (text.length === 0) throw new Error('Agent Reach search returned no text results')
  return bounded(text)
}

/** Register a functional, zero-key public-web baseline after AI Hub authorization. */
export function apply(ctx) {
  ctx.systemPrompt.section({
    name: 'agent-reach',
    order: 125,
    text: 'Use agent_reach_search for current public information, agent_reach_fetch to read a selected source, and agent_reach_doctor only for connectivity diagnosis. Cite returned URLs. Never run or probe agent-reach, mcporter, Exa, or Jina through Bash: this managed plug-in embeds the approved Exa MCP and Jina routes directly, so no separate CLI is installed or required. These tools are read-only and do not provide private-network access or authenticated social accounts.',
  })

  ctx.tools.register(defineTool({
    name: 'agent_reach_search',
    description: 'Search the public web for current information through the AI Hub-authorized Agent Reach plug-in. Returns Markdown with source URLs.',
    parameters: { query: { type: 'string', required: true, description: 'A focused public-web search query.' } },
    output: { schema: { type: 'string' }, render: (_args, value) => [{ type: 'text', text: value }] },
    timeoutMs: 45_000,
    isConcurrencySafe: () => true,
    async execute(args, exec) {
      const query = args.query.trim()
      if (query.length === 0 || query.length > 500) throw new Error('query must contain 1..500 characters')
      return searchText(query, exec.signal)
    },
  }))

  ctx.tools.register(defineTool({
    name: 'agent_reach_fetch',
    description: 'Read one public HTTP(S) source as Markdown through the AI Hub-authorized Agent Reach plug-in. Local and private-network URLs are rejected.',
    parameters: { url: { type: 'string', required: true, description: 'The public HTTP(S) URL to read.' } },
    output: { schema: { type: 'string' }, render: (_args, value) => [{ type: 'text', text: value }] },
    timeoutMs: 45_000,
    isConcurrencySafe: () => true,
    async execute(args, exec) {
      return readText(`${READER_BASE}${publicTarget(args.url)}`, exec.signal)
    },
  }))

  ctx.tools.register(defineTool({
    name: 'agent_reach_doctor',
    description: 'Perform a real connectivity probe for the Agent Reach public-web backend.',
    parameters: {},
    output: { schema: { type: 'string' }, render: (_args, value) => [{ type: 'text', text: value }] },
    timeoutMs: 20_000,
    isConcurrencySafe: () => true,
    async execute(_args, exec) {
      await Promise.all([
        searchText('Example Domain IANA', exec.signal, 20_000),
        readText(`${READER_BASE}https://example.com`, exec.signal, 20_000),
      ])
      return JSON.stringify({
        status: 'ready',
        runtime: 'managed-client-plugin',
        cliRequired: false,
        providers: {
          search: { name: 'Exa MCP', status: 'available', endpoint: SEARCH_MCP },
          reader: { name: 'Jina Reader', status: 'available', endpoint: READER_BASE },
        },
        unavailable: ['authenticated social channels', 'administrator-unapproved MCP providers'],
      }, null, 2)
    },
  }))
}
