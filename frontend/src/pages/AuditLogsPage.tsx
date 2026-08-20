import { FileClock } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { AdminAudit, Page } from '../lib/types'

export function AuditLogsPage() {
  const [page, setPage] = useState<Page<AdminAudit> | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { api<Page<AdminAudit>>('/api/admin/audit-logs?size=100').then(setPage).catch((reason) => setError(reason.message)) }, [])
  return <div className="page"><header className="page-header"><div><p className="page-kicker">管理留痕</p><h1>操作审计</h1></div><FileClock size={22} aria-hidden="true" /></header>{error && <ErrorNotice message={error} />}{!page ? <Loading /> : page.content.length === 0 ? <Empty /> : <div className="table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>对象</th><th>结果</th><th>摘要</th></tr></thead><tbody>{page.content.map((item) => <tr key={item.id}><td>{formatDate(item.occurredAt)}</td><td className="mono">{item.actor}</td><td>{item.action}</td><td><span>{item.targetType}</span><small className="table-subline mono">{item.targetId}</small></td><td><StatusBadge value={item.outcome} /></td><td>{item.detail ?? '-'}</td></tr>)}</tbody></table></div>}</div>
}

function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) }
