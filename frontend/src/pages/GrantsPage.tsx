import { ArrowLeft, ChevronLeft, ChevronRight, ListFilter, PencilLine, Plus, Search, ShieldPlus, SquarePen, Trash2, UserRound } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading, SuccessNotice } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { Capability, GrantSubjectSummary, McpToolGrantDetail, Page, SubjectGrant } from '../lib/types'

export function GrantsPage() {
  const [subjects, setSubjects] = useState<Page<GrantSubjectSummary> | null>(null)
  const [capabilities, setCapabilities] = useState<Capability[]>([])
  const [filter, setFilter] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [pageNumber, setPageNumber] = useState(0)
  const [editor, setEditor] = useState<{ mode: 'create' | 'edit'; workcode?: string } | null>(null)
  const [pendingDelete, setPendingDelete] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setSubjects(null)
    setError('')
    const query = new URLSearchParams({ page: String(pageNumber), size: '30' })
    if (appliedFilter) query.set('keyword', appliedFilter)
    try { setSubjects(await api<Page<GrantSubjectSummary>>(`/api/admin/grants/subjects?${query}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '加载失败') }
  }, [appliedFilter, pageNumber])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    api<Page<Capability>>('/api/admin/capabilities?status=PUBLISHED&directoryOnly=true&size=100')
      .then((page) => setCapabilities(page.content))
      .catch((reason) => setError(reason instanceof Error ? reason.message : '能力目录加载失败'))
  }, [])

  function search(event: FormEvent) {
    event.preventDefault()
    setPageNumber(0)
    setAppliedFilter(filter.trim())
  }

  function clearFilter() {
    setFilter('')
    setPageNumber(0)
    setAppliedFilter('')
  }

  async function revokeSubject(workcode: string) {
    setDeleting(true); setError('')
    try {
      await api(`/api/admin/grants/subjects/${encodeURIComponent(workcode)}`, { method: 'DELETE' })
      setPendingDelete(null)
      if ((subjects?.content.length ?? 0) === 1 && pageNumber > 0) setPageNumber((value) => value - 1)
      else await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '移除失败') }
    finally { setDeleting(false) }
  }

  return <div className="page">
    <header className="page-header"><div><p className="page-kicker">访问控制</p><h1>用户授权</h1></div><button className="primary-button" onClick={() => setEditor({ mode: 'create' })}><Plus size={17} aria-hidden="true" />新增用户授权</button></header>
    <form className="toolbar grant-list-toolbar" onSubmit={search}>
      <div className="grant-filter">
        <label className="compact-field grant-search"><Search size={16} aria-hidden="true" /><input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="按工号筛选" inputMode="numeric" pattern="[0-9]{0,12}" aria-label="按工号筛选" /></label>
        <button className="secondary-button">筛选</button>
        {appliedFilter && <button type="button" className="text-button" onClick={clearFilter}>清除</button>}
      </div>
      {subjects && <span className="list-count" role="status">共 {subjects.totalElements} 位用户</span>}
    </form>
    {error && <ErrorNotice message={error} />}
    {!subjects ? <Loading /> : subjects.content.length === 0 ? <Empty text={appliedFilter ? '没有匹配的已授权用户' : '暂无已授权用户'} /> : <>
      <div className="table-wrap"><table className="grant-subject-table"><thead><tr><th>用户工号</th><th>已授权能力</th><th>有效授权</th><th>最近到期</th><th>状态</th><th className="actions-cell">操作</th></tr></thead><tbody>{subjects.content.map((subject) => <tr key={subject.workcode}>
        <td><div className="subject-identity"><UserRound size={17} aria-hidden="true" /><strong className="mono">{subject.workcode}</strong></div></td>
        <td><CapabilitySummary subject={subject} /></td>
        <td className="mono">{subject.activeGrantCount}</td>
        <td>{subject.nearestExpiry ? formatDate(subject.nearestExpiry) : '长期'}</td>
        <td><StatusBadge value="AUTHORIZED" /></td>
        <td className="actions-cell"><div className="row-actions"><button className="icon-button" title="编辑授权" aria-label={`编辑 ${subject.workcode} 的授权`} onClick={() => setEditor({ mode: 'edit', workcode: subject.workcode })}><SquarePen size={17} /></button><button className="icon-button icon-button--danger" title="移除全部授权" aria-label={`移除 ${subject.workcode} 的全部授权`} onClick={() => setPendingDelete(subject.workcode)}><Trash2 size={17} /></button></div></td>
      </tr>)}</tbody></table></div>
      <footer className="pagination"><span>第 {subjects.number + 1} / {Math.max(subjects.totalPages, 1)} 页</span><div><button className="icon-button" title="上一页" aria-label="上一页" disabled={pageNumber === 0} onClick={() => setPageNumber((value) => value - 1)}><ChevronLeft size={17} /></button><button className="icon-button" title="下一页" aria-label="下一页" disabled={pageNumber + 1 >= subjects.totalPages} onClick={() => setPageNumber((value) => value + 1)}><ChevronRight size={17} /></button></div></footer>
    </>}
    {editor && <GrantEditorModal mode={editor.mode} workcode={editor.workcode} capabilities={capabilities} onClose={() => setEditor(null)} onChanged={() => void load()} />}
    {pendingDelete && <Modal title="移除用户授权" onClose={() => { if (!deleting) setPendingDelete(null) }}><div className="confirm-dialog"><UserRound size={22} aria-hidden="true" /><p>确认移除工号 <strong className="mono">{pendingDelete}</strong> 的全部授权？</p><div className="form-actions"><button className="secondary-button" disabled={deleting} onClick={() => setPendingDelete(null)}>取消</button><button className="danger-button" disabled={deleting} onClick={() => void revokeSubject(pendingDelete)}>{deleting ? '移除中' : '确认移除'}</button></div></div></Modal>}
  </div>
}

function CapabilitySummary({ subject }: { subject: GrantSubjectSummary }) {
  const visible = subject.capabilities.slice(0, 2)
  return <div className="grant-capability-summary">{visible.map((item) => <span key={item.capabilityId} title={`${typeLabel(item.type)} / ${item.name} / ${item.releaseVersion}`}><strong>{item.name}</strong><small>{typeLabel(item.type)}</small></span>)}{subject.capabilities.length > visible.length && <em>另 {subject.capabilities.length - visible.length} 项</em>}</div>
}

function GrantEditorModal({ mode, workcode: initialWorkcode, capabilities, onClose, onChanged }: { mode: 'create' | 'edit'; workcode?: string; capabilities: Capability[]; onClose: () => void; onChanged: () => void }) {
  const [workcode, setWorkcode] = useState(initialWorkcode ?? '')
  const [grants, setGrants] = useState<SubjectGrant[] | null>(mode === 'edit' ? null : [])
  const [capabilityId, setCapabilityId] = useState('')
  const [validFrom, setValidFrom] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [editingGrantId, setEditingGrantId] = useState<string | null>(null)
  const [pendingRevokeId, setPendingRevokeId] = useState<string | null>(null)
  const [toolMcpId, setToolMcpId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const capabilityMap = new Map(capabilities.map((item) => [item.id, item]))

  const loadGrants = useCallback(async () => {
    if (!initialWorkcode) return
    setError('')
    try { setGrants(await api<SubjectGrant[]>(`/api/admin/grants?workcode=${encodeURIComponent(initialWorkcode)}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '授权明细加载失败') }
  }, [initialWorkcode])

  useEffect(() => { if (mode === 'edit') void loadGrants() }, [loadGrants, mode])

  function resetGrantForm() {
    setCapabilityId(''); setValidFrom(''); setValidUntil(''); setEditingGrantId(null)
  }

  function editGrant(grant: SubjectGrant) {
    setCapabilityId(grant.capabilityId)
    setValidFrom(toLocalDateTime(grant.validFrom))
    setValidUntil(grant.validUntil ? toLocalDateTime(grant.validUntil) : '')
    setEditingGrantId(grant.id)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      await api('/api/admin/grants', { method: 'POST', body: JSON.stringify({
        workcode: workcode.trim(), capabilityId,
        validFrom: validFrom ? new Date(validFrom).toISOString() : null,
        validUntil: validUntil ? new Date(validUntil).toISOString() : null,
      }) })
      onChanged()
      if (mode === 'create') onClose()
      else { resetGrantForm(); await loadGrants() }
    } catch (reason) { setError(reason instanceof Error ? reason.message : '保存授权失败') }
    finally { setSaving(false) }
  }

  async function revoke(grantId: string) {
    setSaving(true); setError('')
    try { await api(`/api/admin/grants/${grantId}`, { method: 'DELETE' }); setPendingRevokeId(null); onChanged(); await loadGrants() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '撤销授权失败') }
    finally { setSaving(false) }
  }

  if (toolMcpId && initialWorkcode) {
    return <Modal title="MCP Tool 权限" className="modal--wide" onClose={onClose}>
      <McpToolAccessPanel workcode={initialWorkcode} mcpId={toolMcpId} onBack={() => setToolMcpId(null)} />
    </Modal>
  }

  return <Modal title={mode === 'create' ? '新增用户授权' : '编辑用户授权'} className="modal--wide" onClose={onClose}>
    {mode === 'edit' && <div className="modal-context"><UserRound size={18} aria-hidden="true" /><span>用户工号</span><strong className="mono">{workcode}</strong></div>}
    <form className="form-grid grant-editor-form" onSubmit={submit}>
      {error && <div className="form-full"><ErrorNotice message={error} /></div>}
      {mode === 'create' && <label className="form-full">用户工号<input data-autofocus value={workcode} onChange={(event) => setWorkcode(event.target.value)} inputMode="numeric" pattern="[0-9]{5,12}" maxLength={12} required /></label>}
      <label className="form-full">授权能力<select data-autofocus={mode === 'edit' ? true : undefined} value={capabilityId} onChange={(event) => setCapabilityId(event.target.value)} required><option value="">选择已发布能力</option>{capabilities.map((item) => <option key={item.id} value={item.id}>{typeLabel(item.type)} / {item.name} / {item.releaseVersion}</option>)}</select></label>
      <label>生效时间<input type="datetime-local" value={validFrom} onChange={(event) => setValidFrom(event.target.value)} /></label>
      <label>失效时间<input type="datetime-local" value={validUntil} onChange={(event) => setValidUntil(event.target.value)} /></label>
      <div className="form-actions form-full">{editingGrantId ? <button type="button" className="secondary-button" onClick={resetGrantForm}>取消修改</button> : <button type="button" className="secondary-button" onClick={onClose}>取消</button>}<button className="primary-button" disabled={saving || capabilities.length === 0}><ShieldPlus size={17} aria-hidden="true" />{saving ? '保存中' : editingGrantId ? '更新授权' : '保存授权'}</button></div>
    </form>
    {mode === 'edit' && <section className="grant-detail-list"><div className="section-heading"><h3>授权明细</h3><span>{grants?.length ?? 0} 条</span></div>{grants === null ? <Loading /> : grants.length === 0 ? <Empty text="该用户暂无授权" /> : <div className="table-wrap"><table><thead><tr><th>能力</th><th>生效时间</th><th>失效时间</th><th>状态</th><th className="actions-cell actions-cell--wide">操作</th></tr></thead><tbody>{grants.map((grant) => { const capability = capabilityMap.get(grant.capabilityId); const state = grantState(grant); return <tr key={grant.id}><td><strong>{capability?.name ?? grant.capabilityId}</strong><small className="table-subline">{capability ? typeLabel(capability.type) : '未知能力'}</small></td><td>{formatDate(grant.validFrom)}</td><td>{grant.validUntil ? formatDate(grant.validUntil) : '长期'}</td><td><StatusBadge value={state} /></td><td className="actions-cell actions-cell--wide">{pendingRevokeId === grant.id ? <div className="inline-confirm"><button className="text-button" disabled={saving} onClick={() => setPendingRevokeId(null)}>取消</button><button className="text-button text-button--danger" disabled={saving} onClick={() => void revoke(grant.id)}>确认撤销</button></div> : <div className="row-actions">{capability?.type === 'MCP' && state === 'APPROVED' && <button className="icon-button" title="配置 MCP Tool" aria-label={`配置 ${capability.name} Tool 权限`} onClick={() => setToolMcpId(capability.id)}><ListFilter size={16} /></button>}<button className="icon-button" title="修改授权" aria-label={`修改 ${capability?.name ?? grant.capabilityId} 授权`} onClick={() => editGrant(grant)}><PencilLine size={16} /></button>{grant.enabled && <button className="icon-button icon-button--danger" title="撤销授权" aria-label={`撤销 ${capability?.name ?? grant.capabilityId} 授权`} onClick={() => setPendingRevokeId(grant.id)}><Trash2 size={16} /></button>}</div>}</td></tr> })}</tbody></table></div>}</section>}
  </Modal>
}

function McpToolAccessPanel({ workcode, mcpId, onBack }: { workcode: string; mcpId: string; onBack: () => void }) {
  const [detail, setDetail] = useState<McpToolGrantDetail | null>(null)
  const [pendingToolId, setPendingToolId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      setDetail(await api<McpToolGrantDetail>(`/api/admin/grants/subjects/${encodeURIComponent(workcode)}/mcps/${mcpId}/tools`))
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Tool 权限加载失败') }
  }, [mcpId, workcode])

  useEffect(() => { void load() }, [load])

  async function toggle(toolId: string, excluded: boolean, name: string) {
    setPendingToolId(toolId); setError(''); setSuccess('')
    const path = `/api/admin/grants/subjects/${encodeURIComponent(workcode)}/mcps/${mcpId}/tools/${toolId}/exclusion`
    try {
      await api(path, { method: excluded ? 'DELETE' : 'POST' })
      setSuccess(`${name} 已${excluded ? '恢复' : '取消'}授权`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Tool 权限更新失败') }
    finally { setPendingToolId(null) }
  }

  return <div className="tool-access-panel">
    <button className="text-button back-button" onClick={onBack}><ArrowLeft size={16} aria-hidden="true" />返回用户授权</button>
    {detail && <div className="modal-context"><UserRound size={18} aria-hidden="true" /><span className="mono">{workcode}</span><strong>{detail.mcp.name}</strong></div>}
    {error && <ErrorNotice message={error} />}
    {success && <SuccessNotice message={success} />}
    {detail === null ? <Loading /> : detail.tools.length === 0 ? <Empty text="该 MCP 暂无已发布 Tool" /> : <div className="table-wrap"><table><thead><tr><th>Tool</th><th>稳定引用</th><th>用户可用</th></tr></thead><tbody>{detail.tools.map(({ tool, excluded }) => <tr key={tool.id}><td><strong>{tool.name}</strong><small className="table-subline">{tool.description}</small></td><td className="mono">{tool.externalRef}</td><td><label className="access-toggle"><input type="checkbox" checked={!excluded} disabled={pendingToolId !== null} onChange={() => void toggle(tool.id, excluded, tool.name)} /><span>{pendingToolId === tool.id ? '更新中' : excluded ? '已取消' : '已授权'}</span></label></td></tr>)}</tbody></table></div>}
  </div>
}

function grantState(grant: SubjectGrant): string {
  if (!grant.enabled) return 'REVOKED'
  const now = Date.now()
  if (new Date(grant.validFrom).getTime() > now) return 'PENDING'
  if (grant.validUntil && new Date(grant.validUntil).getTime() <= now) return 'EXPIRED'
  return 'APPROVED'
}

function typeLabel(value: Capability['type']): string { return ({ MCP: 'MCP', TOOL: 'Tool', SKILL: 'Skill', BUNDLE: '能力包', CLIENT_PLUGIN: '客户端插件', INSTRUCTION: '企业指令' })[value] }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
function toLocalDateTime(value: string) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16) }
