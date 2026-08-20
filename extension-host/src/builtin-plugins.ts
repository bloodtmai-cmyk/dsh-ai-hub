import type { Context } from '@deepseek-ai/cordis'
import type { HubPluginManifest, HubPluginRegistration } from './contracts.js'
import { PluginInvocationError } from './plugin-registry.js'

type RecordValue = Record<string, unknown>

const gatewayCatalogManifest: HubPluginManifest = {
  id: 'gateway-catalog',
  displayName: 'Gateway 能力目录',
  description: '规范化 Gateway 发现的 MCP 与 Tool，并交由 Core 执行准入和授权。',
  version: '0.1.0',
  apiVersion: 'v1',
  kind: 'CONNECTOR',
  provides: ['gateway.catalog.normalize'],
  requires: ['core.capability-catalog'],
  permissions: ['network:ai-hub-core'],
  secrets: [],
  uiSlots: [{ id: 'capability-catalog', label: '能力目录', page: 'capabilities' }],
}

const skillArtifactManifest: HubPluginManifest = {
  id: 'skill-artifact',
  displayName: 'Skill 制品',
  description: '承载 Skill 上传制品的结构校验、摘要和后续存储适配。',
  version: '0.1.0',
  apiVersion: 'v1',
  kind: 'PROVIDER',
  provides: ['skill.artifact.contract'],
  requires: ['core.capability-catalog'],
  permissions: ['artifact:read', 'artifact:write'],
  secrets: [],
  uiSlots: [{ id: 'skill-store', label: 'Skill 商店', page: 'capabilities' }],
}

const conversationAuditManifest: HubPluginManifest = {
  id: 'conversation-audit',
  displayName: '对话审计接入',
  description: '声明 Harness 对话审计接入协议；加密、留存与访问审计仍由 Core 负责。',
  version: '0.1.0',
  apiVersion: 'v1',
  kind: 'CONNECTOR',
  provides: ['conversation.audit.contract'],
  requires: ['core.audit-store'],
  permissions: ['audit:ingest'],
  secrets: [],
  uiSlots: [{ id: 'conversation-audit', label: '对话审计', page: 'conversations' }],
}

const manualKeyManifest: HubPluginManifest = {
  id: 'manual-key-maintenance',
  displayName: 'API Key 手工维护',
  description: '声明管理员录入、审批和一次性领取 Key 的界面契约，密文存储仍由 Core 负责。',
  version: '0.1.0',
  apiVersion: 'v1',
  kind: 'PROVIDER',
  provides: ['key.manual-bind'],
  requires: ['core.key-approval'],
  permissions: [],
  secrets: [],
  uiSlots: [{ id: 'api-key', label: 'API Key', page: 'keys' }],
}

const workbuddyLdapAuthManifest: HubPluginManifest = {
  id: 'workbuddy-ldap-auth',
  displayName: 'WorkBuddy LDAP/AD 认证',
  description: '展示 WorkBuddy Gateway 当前 LDAP/AD 认证 Provider 的装配状态；域控凭据和 JWT 签发始终留在 Gateway Core。',
  version: '0.1.0',
  apiVersion: 'v1',
  kind: 'PROVIDER',
  provides: ['identity.ldap.status'],
  requires: ['gateway.identity-provider', 'core.jwt-verifier'],
  permissions: ['network:workbuddy-gateway'],
  secrets: [],
  uiSlots: [],
}

interface IdentityProviderMetadata {
  id: string
  interactionMode: string
  authorizationPath: string
}

type IdentityProviderConfig =
  | { url: URL; error?: never }
  | { url?: never; error: string }

const IDENTITY_PROVIDER_TIMEOUT_MS = 3_000

function applyGatewayCatalogPlugin(ctx: Context): void {
  register(ctx, {
    manifest: gatewayCatalogManifest,
    operations: {
      normalize: async (payload) => normalizeGatewayCatalog(payload),
    },
  })
}

function applySkillArtifactPlugin(ctx: Context): void {
  register(ctx, { manifest: skillArtifactManifest })
}

function applyConversationAuditPlugin(ctx: Context): void {
  register(ctx, { manifest: conversationAuditManifest })
}

function applyManualKeyMaintenancePlugin(ctx: Context): void {
  register(ctx, { manifest: manualKeyManifest })
}

function applyWorkbuddyLdapAuthPlugin(ctx: Context): void {
  const config = identityProviderConfig()
  register(ctx, {
    manifest: workbuddyLdapAuthManifest,
    health: async () => {
      if (config.url === undefined) return { state: 'DEGRADED', detail: config.error }
      try {
        const metadata = await loadIdentityProviderMetadata(config.url)
        if (metadata.id !== 'ldap') {
          return { state: 'DEGRADED', detail: `WorkBuddy 当前使用 ${metadata.id} 认证 Provider` }
        }
        if (metadata.interactionMode !== 'PASSWORD') {
          return { state: 'DEGRADED', detail: 'LDAP/AD Provider 未使用受控密码票据模式' }
        }
        return { state: 'READY', detail: 'WorkBuddy 当前使用 LDAP/AD 认证 Provider' }
      } catch {
        return { state: 'DEGRADED', detail: '无法读取 WorkBuddy 认证 Provider 状态' }
      }
    },
    operations: {
      inspect: async () => {
        if (config.url === undefined) {
          throw new PluginInvocationError(503, 'IDENTITY_PROVIDER_NOT_CONFIGURED', config.error)
        }
        return loadIdentityProviderMetadata(config.url)
      },
    },
  })
}

export const gatewayCatalogPlugin = Object.assign(applyGatewayCatalogPlugin, { inject: ['hubPluginRegistry'] })
export const skillArtifactPlugin = Object.assign(applySkillArtifactPlugin, { inject: ['hubPluginRegistry'] })
export const conversationAuditPlugin = Object.assign(applyConversationAuditPlugin, { inject: ['hubPluginRegistry'] })
export const manualKeyMaintenancePlugin = Object.assign(applyManualKeyMaintenancePlugin, { inject: ['hubPluginRegistry'] })
export const workbuddyLdapAuthPlugin = Object.assign(applyWorkbuddyLdapAuthPlugin, { inject: ['hubPluginRegistry'] })

function register(ctx: Context, registration: HubPluginRegistration): void {
  ctx.effect(() => ctx.hubPluginRegistry.register(registration), `AI Hub plugin: ${registration.manifest.id}`)
}

function normalizeGatewayCatalog(payload: unknown): unknown {
  const body = objectValue(payload, '目录请求无效')
  const capabilities = Array.isArray(body.capabilities) ? body.capabilities : []
  return {
    capabilities: capabilities.map((candidate) => {
      const item = objectValue(candidate, '能力描述无效')
      return {
        type: requiredString(item.type, '能力类型缺失').toUpperCase(),
        externalRef: requiredString(item.externalRef, '能力标识缺失').trim(),
        name: requiredString(item.name, '能力名称缺失').trim(),
        description: optionalString(item.description)?.trim() ?? '',
        parentExternalRef: optionalString(item.parentExternalRef)?.trim(),
        releaseVersion: requiredString(item.releaseVersion, '能力版本缺失').trim(),
        sourceRef: optionalString(item.sourceRef)?.trim(),
      }
    }),
  }
}

function objectValue(
  value: unknown,
  message: string,
  status = 400,
  code = 'INVALID_PAYLOAD',
): RecordValue {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new PluginInvocationError(status, code, message)
  }
  return value as RecordValue
}

function requiredString(value: unknown, message: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new PluginInvocationError(400, 'INVALID_PAYLOAD', message)
  }
  return value
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function identityProviderConfig(): IdentityProviderConfig {
  const explicitURL = process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL?.trim()
  const issuer = process.env.AI_HUB_JWT_ISSUER?.trim()
  const rawURL = explicitURL ?? (issuer === undefined || issuer.length === 0
    ? undefined
    : `${issuer.replace(/\/+$/, '')}/oauth2/identity-provider`)
  if (rawURL === undefined || rawURL.length === 0) {
    return { error: '未配置 WorkBuddy 认证 Provider 地址' }
  }

  try {
    const url = new URL(rawURL)
    if (!['http:', 'https:'].includes(url.protocol)
      || url.username.length > 0
      || url.password.length > 0
      || url.search.length > 0
      || url.hash.length > 0) {
      return { error: 'WorkBuddy 认证 Provider 地址无效' }
    }
    return { url }
  } catch {
    return { error: 'WorkBuddy 认证 Provider 地址无效' }
  }
}

async function loadIdentityProviderMetadata(url: URL): Promise<IdentityProviderMetadata> {
  const response = await fetch(url, {
    headers: { Accept: 'application/json' },
    redirect: 'error',
    signal: AbortSignal.timeout(IDENTITY_PROVIDER_TIMEOUT_MS),
  })
  if (!response.ok) {
    throw new PluginInvocationError(502, 'IDENTITY_PROVIDER_UNAVAILABLE', 'WorkBuddy 认证 Provider 状态不可用')
  }

  const body = objectValue(await response.json(), 'WorkBuddy 认证 Provider 响应无效', 502, 'INVALID_IDENTITY_PROVIDER_RESPONSE')
  const id = requiredProviderString(body.id, '认证 Provider 标识缺失')
  const interactionMode = requiredProviderString(body.interactionMode, '认证 Provider 交互模式缺失')
  const authorizationPath = requiredProviderString(body.authorizationPath, '认证 Provider 授权路径缺失')
  if (!authorizationPath.startsWith('/oauth2/authorize')) {
    throw new PluginInvocationError(502, 'INVALID_IDENTITY_PROVIDER_RESPONSE', '认证 Provider 授权路径无效')
  }
  return { id, interactionMode, authorizationPath }
}

function requiredProviderString(value: unknown, message: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new PluginInvocationError(502, 'INVALID_IDENTITY_PROVIDER_RESPONSE', message)
  }
  return value.trim()
}
