const toneByStatus: Record<string, string> = {
  SUCCESS: 'positive',
  PUBLISHED: 'positive',
  APPROVED: 'positive',
  AUTHORIZED: 'positive',
  AVAILABLE: 'positive',
  READY: 'positive',
  CLAIMED: 'neutral',
  PENDING: 'warning',
  DRAFT: 'warning',
  DISCOVERED: 'warning',
  DEGRADED: 'warning',
  ERROR: 'danger',
  REJECTED: 'danger',
  REVOKED: 'danger',
  DISABLED: 'neutral',
  SUPERSEDED: 'neutral',
  CANCELLED: 'neutral',
  EXPIRED: 'neutral',
}

const labelByStatus: Record<string, string> = {
  SUCCESS: '成功',
  PUBLISHED: '已发布',
  APPROVED: '已通过',
  AUTHORIZED: '已授权',
  AVAILABLE: '待领取',
  READY: '已就绪',
  CLAIMED: '已领取',
  PENDING: '待审批',
  DRAFT: '草稿',
  DISCOVERED: '待准入',
  DEGRADED: '待配置',
  ERROR: '失败',
  REJECTED: '已拒绝',
  REVOKED: '已撤销',
  DISABLED: '已停用',
  SUPERSEDED: '已替换',
  CANCELLED: '已取消',
  EXPIRED: '已过期',
}

export function StatusBadge({ value }: { value: string }) {
  return <span className={`status status--${toneByStatus[value] ?? 'neutral'}`} title={value}>{labelByStatus[value] ?? value}</span>
}
