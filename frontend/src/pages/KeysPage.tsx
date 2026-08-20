import { Ban, Check, Eye, EyeOff, KeyRound, Plus, Search, X } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading, SuccessNotice } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { KeyApplication, Page } from '../lib/types'

type KeyStatusFilter = '' | KeyApplication['status']

const statusFilters: Array<{ value: KeyStatusFilter; label: string }> = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'REVOKED', label: '已撤销' },
]

export function KeysPage() {
  const [page, setPage] = useState<Page<KeyApplication> | null>(null)
  const [status, setStatus] = useState<KeyStatusFilter>('PENDING')
  const [searchInput, setSearchInput] = useState('')
  const [workcode, setWorkcode] = useState('')
  const [pageNumber, setPageNumber] = useState(0)
  const [approveTarget, setApproveTarget] = useState<KeyApplication | null>(null)
  const [rejectTarget, setRejectTarget] = useState<KeyApplication | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<KeyApplication | null>(null)
  const [grantOpen, setGrantOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    const query = new URLSearchParams({ page: String(pageNumber), size: '30' })
    if (status) query.set('status', status)
    if (workcode) query.set('workcode', workcode)
    setError('')
    try {
      setPage(await api<Page<KeyApplication>>(`/api/admin/key-applications?${query}`))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败')
    }
  }, [pageNumber, status, workcode])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!notice) return undefined
    const timer = window.setTimeout(() => setNotice(''), 4_000)
    return () => window.clearTimeout(timer)
  }, [notice])

  function selectStatus(value: KeyStatusFilter) {
    setStatus(value)
    setPageNumber(0)
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setWorkcode(searchInput.trim())
    setPageNumber(0)
  }

  function clearSearch() {
    setSearchInput('')
    setWorkcode('')
    setPageNumber(0)
  }

  function completed(message: string) {
    setNotice(message)
    void load()
  }

  return <div className="page">
    <header className="page-header">
      <div><p className="page-kicker">模型接入</p><h1>API Key 管理</h1></div>
      <button className="primary-button" onClick={() => setGrantOpen(true)}><Plus size={16} aria-hidden="true" />主动授权</button>
    </header>

    <div className="toolbar key-toolbar">
      <div className="segmented" aria-label="申请状态筛选">
        {statusFilters.map((filter) => <button
          key={filter.value || 'ALL'}
          type="button"
          className={status === filter.value ? 'active' : ''}
          aria-pressed={status === filter.value}
          onClick={() => selectStatus(filter.value)}
        >{filter.label}</button>)}
      </div>
      <form className="key-search" role="search" onSubmit={submitSearch}>
        <label className="visually-hidden" htmlFor="key-workcode-search">按工号筛选</label>
        <input id="key-workcode-search" value={searchInput} maxLength={12} placeholder="按工号筛选" onChange={(event) => setSearchInput(event.target.value)} />
        {workcode && <button type="button" className="icon-button" title="清除工号筛选" aria-label="清除工号筛选" onClick={clearSearch}><X size={16} /></button>}
        <button type="submit" className="secondary-button"><Search size={16} aria-hidden="true" />查询</button>
      </form>
    </div>

    {error && <ErrorNotice message={error} />}
    {notice && <SuccessNotice message={notice} />}
    {!page ? <Loading /> : page.content.length === 0 ? <Empty text={status === 'PENDING' ? '当前没有待审批的 API Key 申请' : '没有符合条件的 API Key 记录'} /> : <>
      <div className="table-wrap"><table className="key-table"><thead><tr><th>用户</th><th>用途</th><th>模型接入</th><th>提交时间</th><th>状态</th><th className="actions-cell actions-cell--wide">操作</th></tr></thead><tbody>{page.content.map((item) => <tr key={item.id}>
        <td><strong className="mono">{item.workcode}</strong>{item.reviewer && <span className="table-subline">处理人 {item.reviewer}</span>}</td>
        <td>{item.purpose}{item.decisionComment && <span className="table-subline compact-text" title={item.decisionComment}>{item.decisionComment}</span>}</td>
        <td><span className="mono">{item.provider ?? '-'}</span><span className="table-subline mono">{item.secretMask ?? '-'}</span>{item.baseUrl && <span className="table-subline compact-text" title={item.baseUrl}>{item.baseUrl}</span>}</td>
        <td>{formatDate(item.submittedAt)}</td>
        <td><StatusBadge value={item.status} />{item.keyStatus && <span className="table-subline">{keyStatusLabel(item.keyStatus)}</span>}</td>
        <td className="actions-cell actions-cell--wide"><div className="row-actions">
          {item.status === 'PENDING' && <>
            <button className="text-button key-approve-action" onClick={() => setApproveTarget(item)}><Check size={15} aria-hidden="true" />审批并配置</button>
            <button className="icon-button icon-button--danger" title="拒绝申请" aria-label={`拒绝 ${item.workcode} 的申请`} onClick={() => setRejectTarget(item)}><X size={17} /></button>
          </>}
          {item.status === 'APPROVED' && <button className="icon-button icon-button--danger" title="在 Hub 中标记吊销" aria-label={`吊销 ${item.workcode} 的 API Key`} onClick={() => setRevokeTarget(item)}><Ban size={17} /></button>}
        </div></td>
      </tr>)}</tbody></table></div>
      <div className="pagination"><span>共 {page.totalElements} 条</span><div>
        <button className="secondary-button" disabled={page.number <= 0} onClick={() => setPageNumber((value) => Math.max(0, value - 1))}>上一页</button>
        <button className="secondary-button" disabled={page.number + 1 >= page.totalPages} onClick={() => setPageNumber((value) => value + 1)}>下一页</button>
      </div></div>
    </>}

    {approveTarget && <ApproveModal item={approveTarget} onClose={() => setApproveTarget(null)} onDone={() => { setApproveTarget(null); completed(`已批准 ${approveTarget.workcode} 的 API Key`) }} />}
    {rejectTarget && <RejectModal item={rejectTarget} onClose={() => setRejectTarget(null)} onDone={() => { setRejectTarget(null); completed(`已拒绝 ${rejectTarget.workcode} 的申请`) }} />}
    {revokeTarget && <RevokeModal item={revokeTarget} onClose={() => setRevokeTarget(null)} onDone={() => { setRevokeTarget(null); completed(`已在 Hub 中吊销 ${revokeTarget.workcode} 的 API Key`) }} />}
    {grantOpen && <GrantModal onClose={() => setGrantOpen(false)} onDone={(workcodeValue) => { setGrantOpen(false); completed(`已主动授权 ${workcodeValue} 的 API Key`) }} />}
  </div>
}

function ApproveModal({ item, onClose, onDone }: { item: KeyApplication; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [showKey, setShowKey] = useState(false)
  const [apiKeyError, setApiKeyError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const provider = String(data.get('provider') ?? '').trim()
    const baseUrl = String(data.get('baseUrl') ?? '').trim()
    const apiKey = String(data.get('apiKey') ?? '').trim()
    if (!validateApiKey(apiKey)) {
      setApiKeyError('请输入不含空格的模型 API Key')
      return
    }
    setSaving(true)
    setError('')
    try {
      await api(`/api/admin/key-applications/${item.id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ provider, baseUrl, apiKey }),
      })
      onDone()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '审批失败')
    } finally {
      setSaving(false)
    }
  }

  return <Modal title="审批并保存 API Key" onClose={onClose}>
    <div className="modal-context"><KeyRound size={18} aria-hidden="true" /><span className="mono">{item.workcode}</span><strong>{item.purpose}</strong></div>
    <form className="form-grid" onSubmit={submit}>
      {error && <div className="form-full"><ErrorNotice message={error} /></div>}
      <ModelAccessFields />
      <SecretKeyField show={showKey} error={apiKeyError} autofocus onToggle={() => setShowKey((value) => !value)} onBlur={(value) => setApiKeyError(value && validateApiKey(value) ? '' : '请输入不含空格的模型 API Key')} />
      <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving}>{saving ? '保存中' : '批准并保存'}</button></div>
    </form>
  </Modal>
}

function GrantModal({ onClose, onDone }: { onClose: () => void; onDone: (workcode: string) => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [showKey, setShowKey] = useState(false)
  const [workcodeError, setWorkcodeError] = useState('')
  const [apiKeyError, setApiKeyError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const workcode = String(data.get('workcode') ?? '').trim()
    const provider = String(data.get('provider') ?? '').trim()
    const baseUrl = String(data.get('baseUrl') ?? '').trim()
    const apiKey = String(data.get('apiKey') ?? '').trim()
    const nextWorkcodeError = /^[A-Za-z0-9]{1,12}$/.test(workcode) ? '' : '请输入 1 到 12 位工号'
    const nextKeyError = validateApiKey(apiKey) ? '' : '请输入不含空格的模型 API Key'
    setWorkcodeError(nextWorkcodeError)
    setApiKeyError(nextKeyError)
    if (nextWorkcodeError || nextKeyError) return
    setSaving(true)
    setError('')
    try {
      await api('/api/admin/key-applications/grant', {
        method: 'POST',
        body: JSON.stringify({ workcode, provider, baseUrl, apiKey }),
      })
      onDone(workcode)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '主动授权失败')
    } finally {
      setSaving(false)
    }
  }

  return <Modal title="主动授权 API Key" onClose={onClose}>
    <form className="form-grid" onSubmit={submit}>
      {error && <div className="form-full"><ErrorNotice message={error} /></div>}
      <label className="form-full">用户工号<input name="workcode" maxLength={12} data-autofocus aria-invalid={Boolean(workcodeError)} aria-describedby={workcodeError ? 'grant-workcode-error' : undefined} onBlur={(event) => setWorkcodeError(/^[A-Za-z0-9]{1,12}$/.test(event.currentTarget.value.trim()) ? '' : '请输入 1 到 12 位工号')} required />{workcodeError && <span id="grant-workcode-error" className="field-error">{workcodeError}</span>}</label>
      <ModelAccessFields />
      <SecretKeyField show={showKey} error={apiKeyError} onToggle={() => setShowKey((value) => !value)} onBlur={(value) => setApiKeyError(value && validateApiKey(value) ? '' : '请输入不含空格的模型 API Key')} />
      <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="primary-button" disabled={saving}>{saving ? '保存中' : '授权并保存'}</button></div>
    </form>
  </Modal>
}

function SecretKeyField({ show, error, autofocus = false, onToggle, onBlur }: { show: boolean; error: string; autofocus?: boolean; onToggle: () => void; onBlur: (value: string) => void }) {
  return <label className="form-full">模型 API Key
    <span className="secret-input"><input name="apiKey" type={show ? 'text' : 'password'} autoComplete="new-password" maxLength={4096} data-autofocus={autofocus || undefined} aria-invalid={Boolean(error)} aria-describedby={error ? 'api-key-hint api-key-error' : 'api-key-hint'} onBlur={(event) => onBlur(event.currentTarget.value.trim())} required /><button type="button" className="input-icon-button" title={show ? '隐藏 API Key' : '显示 API Key'} aria-label={show ? '隐藏 API Key' : '显示 API Key'} aria-pressed={show} onClick={onToggle}>{show ? <EyeOff size={17} /> : <Eye size={17} />}</button></span>
    <span id="api-key-hint" className="field-hint">AI Hub 加密保存并仅允许领取一次；模型、预算和有效期由上游模型网关控制</span>
    {error && <span id="api-key-error" className="field-error">{error}</span>}
  </label>
}

function ModelAccessFields() {
  return <>
    <label>Provider 标识<input name="provider" defaultValue="openai-compatible" maxLength={80} pattern="[A-Za-z0-9][A-Za-z0-9._-]*" required /></label>
    <label>模型网关地址<input name="baseUrl" type="url" maxLength={1000} placeholder="https://gateway.example.com/v1" required /></label>
  </>
}

function RejectModal({ item, onClose, onDone }: { item: KeyApplication; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    const data = new FormData(event.currentTarget)
    try {
      await api(`/api/admin/key-applications/${item.id}/reject`, { method: 'POST', body: JSON.stringify({ comment: data.get('comment') }) })
      onDone()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '操作失败')
    } finally {
      setSaving(false)
    }
  }
  return <Modal title="拒绝申请" onClose={onClose}><form className="form-grid" onSubmit={submit}>{error && <div className="form-full"><ErrorNotice message={error} /></div>}<label className="form-full">拒绝原因<textarea name="comment" rows={4} required maxLength={500} data-autofocus /></label><div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button className="danger-button" disabled={saving}>{saving ? '处理中' : '确认拒绝'}</button></div></form></Modal>
}

function RevokeModal({ item, onClose, onDone }: { item: KeyApplication; onClose: () => void; onDone: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function revoke() {
    setSaving(true)
    try {
      await api(`/api/admin/key-applications/${item.id}/revoke`, { method: 'POST' })
      onDone()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '吊销失败')
    } finally {
      setSaving(false)
    }
  }
  return <Modal title="吊销 API Key" onClose={onClose}><div className="confirm-dialog"><Ban size={22} aria-hidden="true" /><div><p>确认在 AI Hub 中吊销工号 <strong className="mono">{item.workcode}</strong> 的 API Key？上游 Provider 中的 Key 仍需同步停用。</p>{error && <ErrorNotice message={error} />}</div><div className="form-actions"><button type="button" className="secondary-button" onClick={onClose}>取消</button><button type="button" className="danger-button" disabled={saving} onClick={() => void revoke()}>{saving ? '处理中' : '确认吊销'}</button></div></div></Modal>
}

function validateApiKey(value: string) {
  return value.length > 0 && value.length <= 4096 && !/\s/.test(value)
}

function keyStatusLabel(value: NonNullable<KeyApplication['keyStatus']>) {
  return { AVAILABLE: '待领取', CLAIMED: '已领取', REVOKED: '已吊销' }[value]
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
