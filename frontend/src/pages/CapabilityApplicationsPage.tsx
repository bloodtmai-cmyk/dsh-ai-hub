import { Check, PackageCheck, X } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { CapabilityApplication, Page } from '../lib/types'

export function CapabilityApplicationsPage() {
  const [page, setPage] = useState<Page<CapabilityApplication> | null>(null)
  const [status, setStatus] = useState('PENDING')
  const [approveTarget, setApproveTarget] = useState<CapabilityApplication | null>(null)
  const [rejectTarget, setRejectTarget] = useState<CapabilityApplication | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const query = new URLSearchParams({ size: '100' }); if (status) query.set('status', status)
    setError('')
    try { setPage(await api<Page<CapabilityApplication>>(`/api/admin/capability-applications?${query}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '加载失败') }
  }, [status])
  useEffect(() => { void load() }, [load])

  return <div className="page">
    <header className="page-header"><div><p className="page-kicker">市场运营</p><h1>能力申请审批</h1></div><label className="header-filter"><span>申请状态</span><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option><option value="PENDING">待审批</option><option value="APPROVED">已通过</option><option value="REJECTED">已拒绝</option></select></label></header>
    {error && <ErrorNotice message={error} />}
    {!page ? <Loading /> : page.content.length === 0 ? <Empty /> : <div className="table-wrap"><table><thead><tr><th>申请人</th><th>能力</th><th>类型</th><th>申请理由</th><th>提交时间</th><th>状态</th><th className="actions-cell">操作</th></tr></thead><tbody>{page.content.map((item) => <tr key={item.id}><td className="mono">{item.workcode}</td><td><strong>{item.capability.name}</strong><small className="table-secondary">{item.capability.releaseVersion}</small></td><td>{typeLabel(item.capability.type)}</td><td>{item.reason}</td><td>{formatDate(item.submittedAt)}</td><td><StatusBadge value={item.status} /></td><td className="actions-cell"><div className="row-actions">{item.status === 'PENDING' && <><button className="icon-button" title="批准并授权" onClick={() => setApproveTarget(item)}><Check size={17} /></button><button className="icon-button icon-button--danger" title="拒绝" onClick={() => setRejectTarget(item)}><X size={17} /></button></>}</div></td></tr>)}</tbody></table></div>}
    {approveTarget && <ApproveModal item={approveTarget} onClose={() => setApproveTarget(null)} onDone={() => { setApproveTarget(null); void load() }} />}
    {rejectTarget && <RejectModal item={rejectTarget} onClose={() => setRejectTarget(null)} onDone={() => { setRejectTarget(null); void load() }} />}
  </div>
}

function ApproveModal({ item, onClose, onDone }: { item: CapabilityApplication; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget)
    try { await api(`/api/admin/capability-applications/${item.id}/approve`, { method: 'POST', body: JSON.stringify({ validUntil: data.get('validUntil') ? new Date(String(data.get('validUntil'))).toISOString() : null, comment: data.get('comment') || null }) }); onDone() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '审批失败') }
  }
  return <Modal title="批准并授权" onClose={onClose}><div className="modal-context"><PackageCheck size={18} /><span>{item.workcode}</span><strong>{item.capability.name}</strong></div><form className="form-grid" onSubmit={submit}>{error && <div className="form-full"><ErrorNotice message={error} /></div>}<label>授权有效期至<input name="validUntil" type="datetime-local" /></label><label className="form-full">审批意见<textarea name="comment" maxLength={500} rows={3} /></label><div className="form-hint form-full">批准后立即生成用户授权；Harness 对账后会自动下载已授权插件。</div><div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button">批准并授权</button></div></form></Modal>
}

function RejectModal({ item, onClose, onDone }: { item: CapabilityApplication; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState('')
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const data = new FormData(event.currentTarget); try { await api(`/api/admin/capability-applications/${item.id}/reject`, { method: 'POST', body: JSON.stringify({ comment: data.get('comment') }) }); onDone() } catch (reason) { setError(reason instanceof Error ? reason.message : '操作失败') } }
  return <Modal title="拒绝申请" onClose={onClose}><form className="form-grid" onSubmit={submit}>{error && <div className="form-full"><ErrorNotice message={error} /></div>}<label className="form-full">拒绝原因<textarea name="comment" rows={4} required maxLength={500} /></label><div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="danger-button">确认拒绝</button></div></form></Modal>
}

function typeLabel(value: CapabilityApplication['capability']['type']) { return ({ MCP: 'MCP 服务', TOOL: '工具', SKILL: 'Skill', BUNDLE: '能力包', CLIENT_PLUGIN: '客户端插件', INSTRUCTION: '企业指令' } as const)[value] }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
