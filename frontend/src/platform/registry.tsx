import {
  Activity,
  Blocks,
  FileClock,
  KeyRound,
  MessageSquareText,
  Puzzle,
  ClipboardCheck,
  FileLock2,
  Menu,
  MonitorUp,
  UsersRound,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { AuditLogsPage } from '../pages/AuditLogsPage'
import { CapabilitiesPage } from '../pages/CapabilitiesPage'
import { CapabilityApplicationsPage } from '../pages/CapabilityApplicationsPage'
import { ConversationsPage } from '../pages/ConversationsPage'
import { DashboardPage } from '../pages/DashboardPage'
import { EnterpriseInstructionsPage } from '../pages/EnterpriseInstructionsPage'
import { GrantsPage } from '../pages/GrantsPage'
import { KeysPage } from '../pages/KeysPage'
import { MenuPermissionsPage } from '../pages/MenuPermissionsPage'
import { DesktopReleasesPage } from '../pages/DesktopReleasesPage'
import { PlatformPluginsPage } from '../pages/PlatformPluginsPage'

export type PageId = 'dashboard' | 'capabilities' | 'instructions' | 'desktopReleases' | 'applications' | 'grants' | 'menuPermissions' | 'plugins' | 'conversations' | 'keys' | 'audit'
export type NavigationSectionId = 'overview' | 'catalog' | 'access' | 'operations'

export interface PageRenderContext {
  navigate: (page: PageId) => void
}

export interface PlatformPageSlot {
  id: PageId
  label: string
  icon: LucideIcon
  section: NavigationSectionId
  owner: 'core' | string
  render: (context: PageRenderContext) => ReactNode
}

export const platformNavigationSections: ReadonlyArray<{ id: NavigationSectionId; label: string }> = [
  { id: 'overview', label: '总览' },
  { id: 'catalog', label: '能力与交付' },
  { id: 'access', label: '访问与接入' },
  { id: 'operations', label: '审计与运行' },
]

export const platformPageSlots: readonly PlatformPageSlot[] = [
  { id: 'dashboard', label: '工作台', icon: Activity, section: 'overview', owner: 'core', render: ({ navigate }) => <DashboardPage onNavigate={navigate} /> },
  { id: 'capabilities', label: '能力目录', icon: Blocks, section: 'catalog', owner: 'gateway-catalog', render: () => <CapabilitiesPage /> },
  { id: 'applications', label: '申请审批', icon: ClipboardCheck, section: 'catalog', owner: 'capability-market', render: () => <CapabilityApplicationsPage /> },
  { id: 'instructions', label: '企业指令', icon: FileLock2, section: 'catalog', owner: 'core', render: () => <EnterpriseInstructionsPage /> },
  { id: 'desktopReleases', label: '客户端升级', icon: MonitorUp, section: 'catalog', owner: 'core', render: () => <DesktopReleasesPage /> },
  { id: 'grants', label: '用户授权', icon: UsersRound, section: 'access', owner: 'core', render: () => <GrantsPage /> },
  { id: 'menuPermissions', label: '菜单权限', icon: Menu, section: 'access', owner: 'core', render: () => <MenuPermissionsPage /> },
  { id: 'keys', label: 'API Key', icon: KeyRound, section: 'access', owner: 'manual-key-maintenance', render: () => <KeysPage /> },
  { id: 'plugins', label: '平台模块', icon: Puzzle, section: 'operations', owner: 'core', render: () => <PlatformPluginsPage /> },
  { id: 'conversations', label: '对话审计', icon: MessageSquareText, section: 'operations', owner: 'conversation-audit', render: () => <ConversationsPage /> },
  { id: 'audit', label: '操作审计', icon: FileClock, section: 'operations', owner: 'core', render: () => <AuditLogsPage /> },
]

export function pageSlot(id: PageId): PlatformPageSlot {
  const slot = platformPageSlots.find((candidate) => candidate.id === id)
  if (slot === undefined) throw new Error(`未知管理页面: ${id}`)
  return slot
}
