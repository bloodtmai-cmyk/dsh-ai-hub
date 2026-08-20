import { Ban, BookOpen, Check, ListTree, Package, PlugZap, RadioTower, Rocket, Search, Upload } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { Capability, CapabilityStatus, CapabilityType, Page } from '../lib/types'

const types: Array<CapabilityType | ''> = ['', 'MCP', 'SKILL', 'BUNDLE', 'CLIENT_PLUGIN']
const statuses: Array<CapabilityStatus | ''> = ['', 'DISCOVERED', 'APPROVED', 'PUBLISHED', 'DISABLED']

export function CapabilitiesPage() {
  const [page, setPage] = useState<Page<Capability> | null>(null)
  const [type, setType] = useState<CapabilityType | ''>('')
  const [status, setStatus] = useState<CapabilityStatus | ''>('')
  const [error, setError] = useState('')
  const [uploadingSkill, setUploadingSkill] = useState(false)
  const [uploadingPlugin, setUploadingPlugin] = useState(false)
  const [creatingBundle, setCreatingBundle] = useState(false)
  const [selectedMcp, setSelectedMcp] = useState<Capability | null>(null)
  const [pendingDisable, setPendingDisable] = useState<Capability | null>(null)
  const [disabling, setDisabling] = useState(false)

  const load = useCallback(async () => {
    setError('')
    const query = new URLSearchParams({ size: '100', directoryOnly: 'true' })
    if (type) query.set('type', type)
    if (status) query.set('status', status)
    try { setPage(await api<Page<Capability>>(`/api/admin/capabilities?${query}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '加载失败') }
  }, [type, status])

  useEffect(() => { void load() }, [load])

  async function transition(item: Capability, action: 'approve' | 'publish') {
    try {
      await api(`/api/admin/capabilities/${item.id}/${action}`, { method: 'POST' })
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '操作失败') }
  }

  async function disable() {
    if (!pendingDisable) return
    setDisabling(true)
    try {
      await api(`/api/admin/capabilities/${pendingDisable.id}/disable`, { method: 'POST' })
      setPendingDisable(null)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '停用失败') }
    finally { setDisabling(false) }
  }

  return (
    <div className="page">
      <header className="page-header"><div><p className="page-kicker">能力治理</p><h1>能力目录</h1></div><div className="header-actions"><button className="secondary-button" onClick={() => setCreatingBundle(true)}><Package size={17} aria-hidden="true" />新建能力包</button><button className="secondary-button" onClick={() => setUploadingSkill(true)}><Upload size={17} aria-hidden="true" />上传 Skill</button><button className="primary-button" onClick={() => setUploadingPlugin(true)}><PlugZap size={17} aria-hidden="true" />上传插件</button></div></header>
      <div className="toolbar">
        <div className="segmented" aria-label="能力类型">{types.map((value) => <button key={value || 'ALL'} className={type === value ? 'active' : ''} onClick={() => setType(value)}>{typeLabel(value)}</button>)}</div>
        <label className="compact-field"><Search size={16} /><select value={status} onChange={(event) => setStatus(event.target.value as CapabilityStatus | '')}>{statuses.map((value) => <option key={value || 'ALL'} value={value}>{statusLabel(value)}</option>)}</select></label>
      </div>
      {error && <ErrorNotice message={error} />}
      {!page ? <Loading /> : page.content.length === 0 ? <Empty /> : <div className="table-wrap"><table><thead><tr><th>能力</th><th>类型</th><th>来源</th><th>版本</th><th>状态</th><th className="actions-cell actions-cell--wide">操作</th></tr></thead><tbody>
        {page.content.map((item) => <tr key={item.id}><td><div className="capability-name"><span className={`type-icon type-icon--${item.type.toLowerCase()}`}>{item.type === 'MCP' ? <RadioTower size={17} /> : item.type === 'BUNDLE' ? <Package size={17} /> : item.type === 'CLIENT_PLUGIN' ? <PlugZap size={17} /> : <BlocksIcon />}</span><div><strong>{item.name}</strong><small>{item.externalRef}</small></div></div></td><td>{typeLabel(item.type)}</td><td>{sourceLabel(item.sourceKind)}</td><td className="mono">{item.releaseVersion}</td><td><StatusBadge value={item.status} /></td><td className="actions-cell actions-cell--wide"><div className="row-actions">
          {item.type === 'MCP' && <button className="icon-button" title="查看 MCP 工具" aria-label={`查看 ${item.name} 的工具`} onClick={() => setSelectedMcp(item)}><ListTree size={17} /></button>}
          {item.status === 'DISCOVERED' && <button className="icon-button" title="准入" onClick={() => transition(item, 'approve')}><Check size={17} /></button>}
          {item.status === 'APPROVED' && <button className="icon-button" title="发布" onClick={() => transition(item, 'publish')}><Rocket size={17} /></button>}
          {item.status !== 'DISABLED' && <button className="icon-button icon-button--danger" title="停用" onClick={() => setPendingDisable(item)}><Ban size={17} /></button>}
        </div></td></tr>)}
      </tbody></table></div>}
      {uploadingSkill && <UploadSkillModal onClose={() => setUploadingSkill(false)} onCreated={() => { setUploadingSkill(false); void load() }} />}
      {uploadingPlugin && <UploadPluginModal onClose={() => setUploadingPlugin(false)} onCreated={() => { setUploadingPlugin(false); void load() }} />}
      {creatingBundle && <CreateBundleModal capabilities={page?.content ?? []} onClose={() => setCreatingBundle(false)} onCreated={() => { setCreatingBundle(false); void load() }} />}
      {selectedMcp && <McpToolsModal mcp={selectedMcp} onClose={() => setSelectedMcp(null)} />}
      {pendingDisable && <Modal title="停用能力" onClose={() => { if (!disabling) setPendingDisable(null) }}><div className="confirm-dialog"><Ban size={22} aria-hidden="true" /><p>确认停用 <strong>{pendingDisable.name}</strong>？{pendingDisable.type === 'MCP' ? '所属 Tool 将同时停止暴露。' : ''}</p><div className="form-actions"><button className="secondary-button" disabled={disabling} onClick={() => setPendingDisable(null)}>取消</button><button className="danger-button" disabled={disabling} onClick={() => void disable()}>{disabling ? '停用中' : '确认停用'}</button></div></div></Modal>}
    </div>
  )
}

function UploadPluginModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); setError('')
    try { await api('/api/admin/client-plugins', { method: 'POST', body: new FormData(event.currentTarget) }); onCreated() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '上传失败') }
    finally { setSaving(false) }
  }

  return <Modal title="上传客户端插件" onClose={onClose}><form className="form-grid" onSubmit={submit}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label className="form-full">插件制品<input name="file" type="file" accept=".zip,application/zip" required /></label>
    <div className="form-hint form-full">ZIP 顶层必须包含 plugin.json、声明的 .mjs 入口及 activation=hot。上传后需依次准入、发布，才会出现在 Harness 插件市场。</div>
    <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving}>{saving ? '上传中' : '上传'}</button></div>
  </form></Modal>
}

function BlocksIcon() { return <BookOpen size={17} /> }

function McpToolsModal({ mcp, onClose }: { mcp: Capability; onClose: () => void }) {
  const [tools, setTools] = useState<Capability[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api<Capability[]>(`/api/admin/capabilities/${mcp.id}/tools`)
      .then(setTools)
      .catch((reason) => setError(reason instanceof Error ? reason.message : 'Tool 列表加载失败'))
  }, [mcp.id])

  return <Modal title="MCP 服务详情" className="modal--wide" onClose={onClose}>
    <div className="modal-context"><RadioTower size={18} aria-hidden="true" /><span>MCP 服务</span><strong>{mcp.name}</strong></div>
    <dl className="detail-meta detail-meta--compact"><div><dt>稳定引用</dt><dd>{mcp.externalRef}</dd></div><div><dt>版本</dt><dd>{mcp.releaseVersion}</dd></div><div><dt>来源</dt><dd>{sourceLabel(mcp.sourceKind)}</dd></div><div><dt>状态</dt><dd><StatusBadge value={mcp.status} /></dd></div></dl>
    <section className="grant-detail-list"><div className="section-heading"><h3>服务工具</h3><span>{tools?.length ?? 0} 项</span></div>{error && <ErrorNotice message={error} />}{tools === null ? <Loading /> : tools.length === 0 ? <Empty text="该 MCP 暂无 Tool" /> : <div className="table-wrap"><table><thead><tr><th>Tool</th><th>版本</th><th>状态</th><th>同步时间</th></tr></thead><tbody>{tools.map((tool) => <tr key={tool.id}><td><strong>{tool.name}</strong><small className="table-subline mono">{tool.externalRef}</small></td><td className="mono">{tool.releaseVersion}</td><td><StatusBadge value={tool.status} /></td><td>{formatDate(tool.updatedAt)}</td></tr>)}</tbody></table></div>}</section>
  </Modal>
}

function UploadSkillModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSaving(true); setError('')
    try { await api('/api/admin/skills', { method: 'POST', body: form }); onCreated() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '上传失败') }
    finally { setSaving(false) }
  }

  return <Modal title="上传 Skill" onClose={onClose}><form className="form-grid" onSubmit={submit}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label className="form-full">Skill 文件<input name="file" type="file" accept=".zip,.md,text/markdown,application/zip" required /></label>
    <label className="form-full">显示名称<input name="displayName" required maxLength={120} /></label>
    <label>版本<input name="releaseVersion" required maxLength={80} placeholder="v1.0.0" /></label>
    <div className="form-hint form-full">支持单个 Markdown Skill 或包含顶层 SKILL.md 的 ZIP，上传后进入待准入状态。</div>
    <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving}>{saving ? '上传中' : '上传'}</button></div>
  </form></Modal>
}

function CreateBundleModal({ capabilities, onClose, onCreated }: { capabilities: Capability[]; onClose: () => void; onCreated: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const candidates = capabilities.filter((item) => !['BUNDLE', 'TOOL', 'INSTRUCTION'].includes(item.type))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const memberIds = form.getAll('memberIds').map(String)
    setSaving(true); setError('')
    try {
      await api('/api/admin/bundles', { method: 'POST', body: JSON.stringify({
        externalRef: form.get('externalRef'), name: form.get('name'), description: form.get('description'),
        releaseVersion: form.get('releaseVersion'), memberIds,
      }) })
      onCreated()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '创建失败') }
    finally { setSaving(false) }
  }

  return <Modal title="新建能力包" onClose={onClose}><form className="form-grid" onSubmit={submit}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label className="form-full">名称<input name="name" required maxLength={120} /></label>
    <label className="form-full">稳定引用<input name="externalRef" required maxLength={240} placeholder="inventory-assistant" /></label>
    <label>版本<input name="releaseVersion" required maxLength={80} placeholder="v1.0.0" /></label>
    <label className="form-full">说明<textarea name="description" required maxLength={1000} rows={3} /></label>
    <fieldset className="member-picker form-full"><legend>包含能力</legend>{candidates.length === 0 ? <span>暂无可选能力</span> : candidates.map((item) => <label key={item.id}><input type="checkbox" name="memberIds" value={item.id} /><span><strong>{item.name}</strong><small>{item.type} / {item.releaseVersion} / {item.status}</small></span></label>)}</fieldset>
    <div className="form-hint form-full">Tool 随 MCP 服务自动包含；发布时所有成员必须已发布。</div>
    <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving || candidates.length === 0}>{saving ? '创建中' : '创建'}</button></div>
  </form></Modal>
}

function typeLabel(value: CapabilityType | '') {
  return ({ '': '全部', MCP: 'MCP 服务', TOOL: '工具', SKILL: 'Skill', BUNDLE: '能力包', CLIENT_PLUGIN: '客户端插件', INSTRUCTION: '企业指令' } as const)[value]
}

function sourceLabel(value: Capability['sourceKind']) {
  return ({ GATEWAY: '网关同步', UPLOAD: '平台上传', MANUAL: '人工登记' } as const)[value]
}

function statusLabel(value: CapabilityStatus | '') {
  return ({ '': '全部状态', DISCOVERED: '待准入', APPROVED: '已通过', PUBLISHED: '已发布', DISABLED: '已停用' } as const)[value]
}

function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
