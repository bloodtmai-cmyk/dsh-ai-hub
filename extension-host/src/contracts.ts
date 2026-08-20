export type HubPluginKind = 'CONNECTOR' | 'PROVIDER'
export type HubPluginState = 'READY' | 'DEGRADED'

export interface HubUiSlot {
  id: string
  label: string
  page: string
}

export interface HubPluginManifest {
  id: string
  displayName: string
  description: string
  version: string
  apiVersion: 'v1'
  kind: HubPluginKind
  provides: string[]
  requires: string[]
  permissions: string[]
  secrets: string[]
  uiSlots: HubUiSlot[]
}

export interface HubPluginHealth {
  state: HubPluginState
  detail?: string
}

export interface HubPluginDescriptor extends HubPluginManifest, HubPluginHealth {
  registeredAt: string
}

export type HubPluginOperation = (payload: unknown) => Promise<unknown>

export interface HubPluginRegistration {
  manifest: HubPluginManifest
  health?: () => HubPluginHealth | Promise<HubPluginHealth>
  operations?: Record<string, HubPluginOperation>
}
