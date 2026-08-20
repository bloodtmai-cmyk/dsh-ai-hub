import { Download, MonitorUp, Rocket, Upload } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading, SuccessNotice } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api, hubUrl, upload } from '../lib/api'
import type { DesktopRelease, DesktopReleasePlatform, Page } from '../lib/types'

const platformLabels: Record<DesktopReleasePlatform, string> = {
  MAC_ARM64: 'macOS Apple Silicon',
  MAC_X64: 'macOS Intel',
  WINDOWS_X64: 'Windows x64',
}

export function DesktopReleasesPage() {
  const [releases, setReleases] = useState<DesktopRelease[] | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [pendingPublish, setPendingPublish] = useState<DesktopRelease | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const page = await api<Page<DesktopRelease>>('/api/admin/desktop-releases?size=100')
      setReleases(page.content)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '客户端版本加载失败')
    }
  }, [])

  useEffect(() => { void load() }, [load])

  async function publish() {
    if (!pendingPublish) return
    setPublishing(true); setError(''); setSuccess('')
    try {
      await api(`/api/admin/desktop-releases/${pendingPublish.id}/publish`, { method: 'POST' })
      setSuccess(`${platformLabels[pendingPublish.platform]} ${pendingPublish.version} 已发布`)
      setPendingPublish(null)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '发布失败') }
    finally { setPublishing(false) }
  }

  const current = pendingPublish
    ? releases?.find((item) => item.platform === pendingPublish.platform && item.status === 'PUBLISHED')
    : undefined

  return <div className="page">
    <header className="page-header">
      <div><p className="page-kicker">桌面交付</p><h1>客户端升级</h1></div>
      <button className="primary-button" onClick={() => setUploadOpen(true)}><Upload size={17} aria-hidden="true" />上传版本</button>
    </header>
    {error && <ErrorNotice message={error} />}
    {success && <SuccessNotice message={success} />}
    {releases === null ? <Loading /> : releases.length === 0 ? <Empty text="暂无客户端版本" /> : <div className="table-wrap"><table className="release-table"><thead><tr><th>平台与版本</th><th>状态</th><th>安装制品</th><th>SHA-256</th><th>发布时间</th><th className="actions-cell">操作</th></tr></thead><tbody>
      {releases.map((item) => <tr key={item.id}>
        <td><div className="capability-name"><span className="type-icon type-icon--release"><MonitorUp size={17} /></span><div><strong>{platformLabels[item.platform]}</strong><small className="mono">{item.version}</small></div></div></td>
        <td><StatusBadge value={item.status} /></td>
        <td className="release-artifact"><span className="compact-text" title={item.fileName}>{item.fileName}</span><small className="table-subline release-notes" title={item.releaseNotes}>{formatBytes(item.sizeBytes)} · {item.releaseNotes}</small></td>
        <td><code className="checksum" title={item.sha256}>{item.sha256.slice(0, 12)}</code></td>
        <td>{item.publishedAt ? formatDate(item.publishedAt) : '未发布'}</td>
        <td className="actions-cell"><div className="row-actions">
          <a className="icon-button" href={hubUrl(`/api/admin/desktop-releases/${item.id}/artifact`)} title="下载安装制品" aria-label={`下载 ${platformLabels[item.platform]} ${item.version}`}><Download size={17} /></a>
          {item.status === 'DRAFT' && <button className="icon-button" title="发布版本" aria-label={`发布 ${item.version}`} onClick={() => setPendingPublish(item)}><Rocket size={17} /></button>}
        </div></td>
      </tr>)}
    </tbody></table></div>}
    {uploadOpen && <UploadReleaseModal onClose={() => setUploadOpen(false)} onCreated={() => { setUploadOpen(false); setSuccess('客户端版本已上传为草稿'); void load() }} />}
    {pendingPublish && <Modal title="发布客户端版本" onClose={() => { if (!publishing) setPendingPublish(null) }}><div className="confirm-dialog confirm-dialog--release"><MonitorUp size={22} aria-hidden="true" /><div><p>确认发布 <strong>{platformLabels[pendingPublish.platform]}</strong> <span className="mono">{pendingPublish.version}</span>？</p><dl className="publish-impact"><div><dt>当前版本</dt><dd className="mono">{current?.version ?? '无'}</dd></div><div><dt>升级方式</dt><dd>用户确认安装</dd></div><div><dt>校验</dt><dd>SHA-256</dd></div></dl></div><div className="form-actions"><button className="secondary-button" disabled={publishing} onClick={() => setPendingPublish(null)}>取消</button><button className="primary-button" disabled={publishing} onClick={() => void publish()}>{publishing ? '发布中' : '确认发布'}</button></div></div></Modal>}
  </div>
}

function UploadReleaseModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [progress, setProgress] = useState(0)
  const [platform, setPlatform] = useState<DesktopReleasePlatform>('MAC_ARM64')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); setProgress(0); setError('')
    try {
      await upload('/api/admin/desktop-releases', new FormData(event.currentTarget), setProgress)
      onCreated()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '上传失败') }
    finally { setSaving(false) }
  }

  const accept = platform === 'WINDOWS_X64' ? '.exe,application/vnd.microsoft.portable-executable' : '.dmg,.pkg'
  return <Modal title="上传客户端版本" onClose={() => { if (!saving) onClose() }}><form className="form-grid" onSubmit={submit} aria-busy={saving}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label>目标平台<select name="platform" value={platform} onChange={(event) => setPlatform(event.target.value as DesktopReleasePlatform)} disabled={saving}>{Object.entries(platformLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    <label>版本<input name="version" required maxLength={80} placeholder="0.1.0-rc.18" pattern="[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?" disabled={saving} /></label>
    <label className="form-full">安装制品<input name="file" type="file" accept={accept} required disabled={saving} /></label>
    <label className="form-full">升级说明<textarea name="releaseNotes" required maxLength={4000} rows={5} disabled={saving} /></label>
    {saving && <div className="upload-progress form-full" aria-live="polite"><progress max="100" value={progress} /><span>{progress}%</span></div>}
    <div className="form-actions form-full"><button type="button" className="secondary-button" onClick={onClose} disabled={saving}>取消</button><button className="primary-button" disabled={saving}>{saving ? '上传中' : '上传草稿'}</button></div>
  </form></Modal>
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

function formatBytes(value: number) {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MiB`
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KiB`
  return `${value} B`
}
