export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

export type CapabilityType = 'MCP' | 'TOOL' | 'SKILL' | 'BUNDLE' | 'CLIENT_PLUGIN' | 'INSTRUCTION'
export type CapabilityStatus = 'DISCOVERED' | 'APPROVED' | 'PUBLISHED' | 'DISABLED'

export type Capability = {
  id: string
  type: CapabilityType
  parentId?: string
  sourceKind: 'GATEWAY' | 'UPLOAD' | 'MANUAL'
  externalRef: string
  name: string
  description: string
  releaseVersion: string
  sourceRef?: string
  integrityHash?: string
  status: CapabilityStatus
  createdAt: string
  updatedAt: string
}

export type SubjectGrant = {
  id: string
  workcode: string
  capabilityId: string
  validFrom: string
  validUntil?: string
  enabled: boolean
  createdBy: string
  createdAt: string
  revokedAt?: string
}

export type GrantSubjectCapability = {
  capabilityId: string
  type: CapabilityType
  name: string
  releaseVersion: string
  validUntil?: string
}

export type GrantSubjectSummary = {
  workcode: string
  capabilities: GrantSubjectCapability[]
  activeGrantCount: number
  nearestExpiry?: string
}

export type McpToolGrantDetail = {
  mcp: Pick<Capability, 'id' | 'type' | 'externalRef' | 'name' | 'description' | 'releaseVersion' | 'sourceRef' | 'integrityHash'>
  tools: Array<{
    tool: Pick<Capability, 'id' | 'type' | 'externalRef' | 'name' | 'description' | 'releaseVersion' | 'sourceRef' | 'integrityHash'>
    excluded: boolean
  }>
}

export type MenuPermissionSubjectType = 'USER' | 'DEPARTMENT'
export type MenuKey = 'PLUGIN_MARKET'

export type MenuPermissionGrant = {
  id: string
  subjectType: MenuPermissionSubjectType
  subjectRef: string
  menuKey: MenuKey
  enabled: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
  revokedAt?: string
}

export type ConversationSummary = {
  id: string
  workcode: string
  sessionId: string
  turnId: string
  model: string
  startedAt: string
  completedAt?: string
  inputTokens: number
  outputTokens: number
  latencyMs?: number
  status: 'SUCCESS' | 'ERROR' | 'CANCELLED'
  errorCode?: string
}

export type ConversationDetail = ConversationSummary & {
  clientInstallationId: string
  userMessage: string
  assistantMessage: string
  receivedAt: string
}

export type KeyApplication = {
  id: string
  workcode: string
  purpose: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED'
  submittedAt: string
  reviewedAt?: string
  reviewer?: string
  decisionComment?: string
  secretMask?: string
  provider?: string
  baseUrl?: string
  keyStatus?: 'AVAILABLE' | 'CLAIMED' | 'REVOKED'
  claimedAt?: string
}

export type CapabilityApplication = {
  id: string
  workcode: string
  capability: Pick<Capability, 'id' | 'type' | 'externalRef' | 'name' | 'description' | 'releaseVersion' | 'sourceRef' | 'integrityHash'>
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  reviewedAt?: string
  reviewer?: string
  decisionComment?: string
}

export type AdminAudit = {
  id: string
  actor: string
  action: string
  targetType: string
  targetId?: string
  outcome: string
  detail?: string
  occurredAt: string
}

export type PlatformPlugin = {
  id: string
  displayName: string
  description: string
  version: string
  apiVersion: string
  kind: 'CONNECTOR' | 'PROVIDER'
  provides: string[]
  requires: string[]
  permissions: string[]
  secrets: string[]
  uiSlots: Array<{ id: string; label: string; page: string }>
  state: 'READY' | 'DEGRADED'
  detail?: string
  registeredAt: string
}

export type PlatformPluginInventory = {
  enabled: boolean
  reachable: boolean
  plugins: PlatformPlugin[]
  message?: string
}

export type DesktopReleasePlatform = 'MAC_ARM64' | 'MAC_X64' | 'WINDOWS_X64'
export type DesktopReleaseStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED'

export type DesktopRelease = {
  id: string
  version: string
  platform: DesktopReleasePlatform
  status: DesktopReleaseStatus
  fileName: string
  mediaType: string
  sizeBytes: number
  sha256: string
  releaseNotes: string
  createdBy: string
  createdAt: string
  publishedBy?: string
  publishedAt?: string
  supersededAt?: string
}
