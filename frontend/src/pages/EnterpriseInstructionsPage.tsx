import { Check, Download, FileLock2, Rocket, Upload } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading, SuccessNotice } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api, hubUrl } from '../lib/api'
import type { Capability, Page } from '../lib/types'

export function EnterpriseInstructionsPage() {
  const [instructions, setInstructions] = useState<Capability[] | null>(null)
  const [uploadBase, setUploadBase] = useState<Capability | null | undefined>(undefined)
  const [pendingPublish, setPendingPublish] = useState<Capability | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const page = await api<Page<Capability>>('/api/admin/capabilities?type=INSTRUCTION&size=100')
      setInstructions(page.content)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业指令加载失败')
    }
  }, [])

  useEffect(() => { void load() }, [load])

  async function approve(item: Capability) {
    setError(''); setSuccess('')
    try {
      await api(`/api/admin/capabilities/${item.id}/approve`, { method: 'POST' })
      setSuccess(`版本 ${item.releaseVersion} 已通过准入`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '准入失败') }
  }

  async function publish() {
    if (!pendingPublish) return
    setPublishing(true); setError(''); setSuccess('')
    try {
      await api(`/api/admin/capabilities/${pendingPublish.id}/publish`, { method: 'POST' })
      setSuccess(`版本 ${pendingPublish.releaseVersion} 已发布并全员生效`)
      setPendingPublish(null)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '发布失败') }
    finally { setPublishing(false) }
  }

  const current = pendingPublish
    ? instructions?.find((item) => item.externalRef === pendingPublish.externalRef && item.status === 'PUBLISHED')
    : undefined

  return <div className="page">
    <header className="page-header">
      <div><p className="page-kicker">企业策略</p><h1>企业指令</h1></div>
      <button className="primary-button" onClick={() => setUploadBase(null)}><Upload size={17} aria-hidden="true" />上传版本</button>
    </header>
    {error && <ErrorNotice message={error} />}
    {success && <SuccessNotice message={success} />}
    {instructions === null ? <Loading /> : instructions.length === 0 ? <Empty text="暂无企业指令" /> : <div className="table-wrap"><table><thead><tr><th>企业指令</th><th>版本</th><th>状态</th><th>更新时间</th><th className="actions-cell">操作</th></tr></thead><tbody>
      {instructions.map((item) => <tr key={item.id}>
        <td><div className="capability-name"><span className="type-icon type-icon--instruction"><FileLock2 size={17} /></span><div><strong>{item.name}</strong><small>{item.externalRef}</small></div></div></td>
        <td className="mono">{item.releaseVersion}</td>
        <td><StatusBadge value={item.status} /></td>
        <td>{formatDate(item.updatedAt)}</td>
        <td className="actions-cell"><div className="row-actions">
          <a className="icon-button" href={hubUrl(`/api/admin/instructions/${item.id}/artifact`)} title="下载版本" aria-label={`下载 ${item.name} ${item.releaseVersion}`}><Download size={17} /></a>
          {item.status === 'DISCOVERED' && <button className="icon-button" title="准入版本" aria-label={`准入 ${item.releaseVersion}`} onClick={() => void approve(item)}><Check size={17} /></button>}
          {item.status === 'APPROVED' && <button className="icon-button" title="发布版本" aria-label={`发布 ${item.releaseVersion}`} onClick={() => setPendingPublish(item)}><Rocket size={17} /></button>}
          {item.status === 'PUBLISHED' && <button className="icon-button" title="上传新版本" aria-label={`更新 ${item.name}`} onClick={() => setUploadBase(item)}><Upload size={17} /></button>}
        </div></td>
      </tr>)}
    </tbody></table></div>}
    {uploadBase !== undefined && <UploadInstructionModal base={uploadBase ?? undefined} onClose={() => setUploadBase(undefined)} onCreated={() => { setUploadBase(undefined); setSuccess('企业指令版本已上传，等待准入'); void load() }} />}
    {pendingPublish && <Modal title="发布企业指令" onClose={() => { if (!publishing) setPendingPublish(null) }}><div className="confirm-dialog confirm-dialog--policy"><FileLock2 size={22} aria-hidden="true" /><div><p>确认发布 <strong>{pendingPublish.name}</strong> <span className="mono">{pendingPublish.releaseVersion}</span>？</p><dl className="publish-impact"><div><dt>当前版本</dt><dd className="mono">{current?.releaseVersion ?? '无'}</dd></div><div><dt>生效范围</dt><dd>全员</dd></div><div><dt>生效时点</dt><dd>下一次模型调用</dd></div></dl></div><div className="form-actions"><button className="secondary-button" disabled={publishing} onClick={() => setPendingPublish(null)}>取消</button><button className="primary-button" disabled={publishing} onClick={() => void publish()}>{publishing ? '发布中' : '确认发布'}</button></div></div></Modal>}
  </div>
}

function UploadInstructionModal({ base, onClose, onCreated }: { base?: Capability; onClose: () => void; onCreated: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); setError('')
    try {
      await api('/api/admin/instructions', { method: 'POST', body: new FormData(event.currentTarget) })
      onCreated()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '上传失败') }
    finally { setSaving(false) }
  }

  return <Modal title={base ? '上传企业指令新版本' : '上传企业指令'} onClose={onClose}><form className="form-grid" onSubmit={submit} aria-busy={saving}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label className="form-full">指令文件<input name="file" type="file" accept=".md,text/markdown" required /></label>
    <label className="form-full">稳定标识<input name="instructionId" required maxLength={120} defaultValue={base?.externalRef} readOnly={Boolean(base)} placeholder="example-enterprise-baseline" pattern="[a-z0-9]+(?:-[a-z0-9]+)*" /></label>
    <label className="form-full">显示名称<input name="displayName" required maxLength={120} defaultValue={base?.name} /></label>
    <label>新版本<input name="releaseVersion" required maxLength={80} placeholder="1.2.0" pattern="[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?" /></label>
    <label className="form-full">说明<textarea name="description" required maxLength={1000} rows={3} defaultValue={base?.description} /></label>
    <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose} disabled={saving}>取消</button><button className="primary-button" disabled={saving}>{saving ? '上传中' : '上传'}</button></div>
  </form></Modal>
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
