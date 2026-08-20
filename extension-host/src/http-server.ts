import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { timingSafeEqual } from 'node:crypto'
import type { Context } from '@deepseek-ai/cordis'
import { PluginInvocationError } from './plugin-registry.js'

const MAX_BODY_BYTES = 1024 * 1024

export interface HttpServerConfig {
  host: string
  port: number
  apiKey: string
}

function applyExtensionHttpServer(ctx: Context, config: HttpServerConfig): void {
  if (config.host !== '127.0.0.1' && config.host !== '::1') {
    throw new Error('AI Hub Extension Host must bind to loopback')
  }
  if (config.apiKey.length < 24) throw new Error('AI Hub Extension Host API key must contain at least 24 characters')

  const server = createServer((request, response) => {
    void route(ctx, config, request, response).catch((error: unknown) => {
      writeProblem(response, error)
    })
  })
  server.listen(config.port, config.host)
  ctx.effect(() => async () => {
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
  }, 'AI Hub Extension Host HTTP server')
}

export const extensionHttpServer = Object.assign(applyExtensionHttpServer, { inject: ['hubPluginRegistry'] })

async function route(ctx: Context, config: HttpServerConfig, request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? '/', `http://${config.host}:${config.port}`)
  if (request.method === 'GET' && url.pathname === '/health') {
    writeJson(response, 200, { status: 'UP' })
    return
  }
  if (!authorized(request, config.apiKey)) throw new PluginInvocationError(401, 'UNAUTHORIZED', '服务身份无效')
  if (request.method === 'GET' && url.pathname === '/internal/plugins') {
    writeJson(response, 200, { plugins: await ctx.hubPluginRegistry.list() })
    return
  }
  const match = /^\/internal\/plugins\/([a-z0-9-]+)\/operations\/([a-z0-9-]+)$/.exec(url.pathname)
  if (request.method === 'POST' && match !== null) {
    const pluginId = match[1]
    const operation = match[2]
    if (pluginId === undefined || operation === undefined) {
      throw new PluginInvocationError(404, 'NOT_FOUND', '接口不存在')
    }
    const payload = await readJson(request)
    writeJson(response, 200, await ctx.hubPluginRegistry.invoke(pluginId, operation, payload))
    return
  }
  throw new PluginInvocationError(404, 'NOT_FOUND', '接口不存在')
}

function authorized(request: IncomingMessage, expected: string): boolean {
  const authorization = request.headers.authorization
  if (authorization?.startsWith('Bearer ') !== true) return false
  const actual = Buffer.from(authorization.slice(7))
  const wanted = Buffer.from(expected)
  return actual.length === wanted.length && timingSafeEqual(actual, wanted)
}

async function readJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = []
  let total = 0
  for await (const chunk of request) {
    const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
    total += bytes.length
    if (total > MAX_BODY_BYTES) throw new PluginInvocationError(413, 'PAYLOAD_TOO_LARGE', '请求内容过大')
    chunks.push(bytes)
  }
  if (chunks.length === 0) return {}
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown
  } catch {
    throw new PluginInvocationError(400, 'INVALID_JSON', '请求 JSON 无效')
  }
}

function writeProblem(response: ServerResponse, error: unknown): void {
  if (error instanceof PluginInvocationError) {
    writeJson(response, error.status, { code: error.code, message: error.message })
    return
  }
  writeJson(response, 500, { code: 'PLUGIN_HOST_ERROR', message: '平台插件执行失败' })
}

function writeJson(response: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
  })
  response.end(payload)
}
