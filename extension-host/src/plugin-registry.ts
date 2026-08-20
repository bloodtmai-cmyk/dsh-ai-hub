import { Service, type Context } from '@deepseek-ai/cordis'
import type {
  HubPluginDescriptor,
  HubPluginHealth,
  HubPluginManifest,
  HubPluginRegistration,
} from './contracts.js'

declare module '@deepseek-ai/cordis' {
  interface Context {
    hubPluginRegistry: HubPluginRegistry
  }
}

interface RegisteredPlugin extends HubPluginRegistration {
  registeredAt: string
}

const ID_PATTERN = /^[a-z][a-z0-9-]{2,63}$/

export class HubPluginRegistry extends Service {
  private readonly plugins = new Map<string, RegisteredPlugin>()

  constructor(ctx: Context) {
    super(ctx, 'hubPluginRegistry')
  }

  register(registration: HubPluginRegistration): () => void {
    validateManifest(registration.manifest)
    const id = registration.manifest.id
    if (this.plugins.has(id)) throw new Error(`duplicate AI Hub plugin: ${id}`)
    this.plugins.set(id, { ...registration, registeredAt: new Date().toISOString() })
    return () => { this.plugins.delete(id) }
  }

  async list(): Promise<HubPluginDescriptor[]> {
    const descriptors = await Promise.all([...this.plugins.values()].map(async (plugin) => {
      const health = await safeHealth(plugin)
      return {
        ...plugin.manifest,
        ...health,
        registeredAt: plugin.registeredAt,
      }
    }))
    return descriptors.sort((left, right) => left.id.localeCompare(right.id))
  }

  async invoke(pluginId: string, operation: string, payload: unknown): Promise<unknown> {
    const plugin = this.plugins.get(pluginId)
    if (plugin === undefined) throw new PluginInvocationError(404, 'PLUGIN_NOT_FOUND', '平台插件不存在')
    const handler = plugin.operations?.[operation]
    if (handler === undefined) throw new PluginInvocationError(404, 'OPERATION_NOT_FOUND', '平台插件操作不存在')
    return handler(payload)
  }
}

export class PluginInvocationError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

async function safeHealth(plugin: RegisteredPlugin): Promise<HubPluginHealth> {
  try {
    return await plugin.health?.() ?? { state: 'READY' }
  } catch {
    return { state: 'DEGRADED', detail: '健康检查失败' }
  }
}

function validateManifest(manifest: HubPluginManifest): void {
  if (!ID_PATTERN.test(manifest.id)) throw new Error(`invalid AI Hub plugin id: ${manifest.id}`)
  if (manifest.apiVersion !== 'v1') throw new Error(`unsupported AI Hub plugin API: ${manifest.apiVersion}`)
  if (manifest.displayName.trim().length === 0 || manifest.version.trim().length === 0) {
    throw new Error(`incomplete AI Hub plugin manifest: ${manifest.id}`)
  }
  const slotIds = new Set<string>()
  for (const slot of manifest.uiSlots) {
    if (slotIds.has(slot.id)) throw new Error(`duplicate UI slot in plugin ${manifest.id}: ${slot.id}`)
    slotIds.add(slot.id)
  }
}
