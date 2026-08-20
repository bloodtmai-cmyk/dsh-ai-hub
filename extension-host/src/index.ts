import { Context } from '@deepseek-ai/cordis'
import {
  conversationAuditPlugin,
  gatewayCatalogPlugin,
  manualKeyMaintenancePlugin,
  skillArtifactPlugin,
  workbuddyLdapAuthPlugin,
} from './builtin-plugins.js'
import { extensionHttpServer } from './http-server.js'
import { HubPluginRegistry } from './plugin-registry.js'

const host = process.env.AI_HUB_EXTENSION_HOST_BIND_ADDRESS ?? '127.0.0.1'
const port = integerEnv('AI_HUB_EXTENSION_HOST_PORT', 8091)
const apiKey = process.env.AI_HUB_EXTENSION_HOST_API_KEY ?? ''

const root = new Context()
await root.plugin(HubPluginRegistry)
await root.plugin(gatewayCatalogPlugin)
await root.plugin(skillArtifactPlugin)
await root.plugin(conversationAuditPlugin)
await root.plugin(manualKeyMaintenancePlugin)
await root.plugin(workbuddyLdapAuthPlugin)
await root.plugin(extensionHttpServer, { host, port, apiKey })

let stopping = false
const shutdown = async () => {
  if (stopping) return
  stopping = true
  await root.fiber.dispose()
}
process.once('SIGINT', () => { void shutdown().finally(() => process.exit(0)) })
process.once('SIGTERM', () => { void shutdown().finally(() => process.exit(0)) })

function integerEnv(name: string, fallback: number): number {
  const raw = process.env[name]
  if (raw === undefined || raw.length === 0) return fallback
  const value = Number.parseInt(raw, 10)
  if (!Number.isSafeInteger(value) || value < 1 || value > 65535) throw new Error(`${name} is invalid`)
  return value
}
