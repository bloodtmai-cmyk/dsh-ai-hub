import { Building2, PencilLine, Plus, Search, Store, Trash2, UserRound } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { Empty, ErrorNotice, Loading, SuccessNotice } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { MenuKey, MenuPermissionGrant, MenuPermissionSubjectType, Page } from '../lib/types'

type SubjectFilter = MenuPermissionSubjectType | ''

export function MenuPermissionsPage() {
  const [page, setPage] = useState<Page<MenuPermissionGrant> | null>(null)
  const [subjectType, setSubjectType] = useState<SubjectFilter>('')
  const [subjectRef, setSubjectRef] = useState('')
  const [appliedRef, setAppliedRef] = useState('')
  const [includeRevoked, setIncludeRevoked] = useState(false)
  const [editor, setEditor] = useState<MenuPermissionGrant | 'create' | null>(null)
  const [pendingRevoke, setPendingRevoke] = useState<MenuPermissionGrant | null>(null)
  const [revoking, setRevoking] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const load = useCallback(async () => {
    setPage(null); setError('')
    const query = new URLSearchParams({ size: '100', includeRevoked: String(includeRevoked) })
    if (subjectType) query.set('subjectType', subjectType)
    if (appliedRef) query.set('subjectRef', appliedRef)
    try { setPage(await api<Page<MenuPermissionGrant>>(`/api/admin/menu-permissions?${query}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '菜单权限加载失败') }
  }, [appliedRef, includeRevoked, subjectType])

  useEffect(() => { void load() }, [load])

  function search(event: FormEvent) {
    event.preventDefault()
    setAppliedRef(subjectRef.trim())
  }

  async function revoke() {
    if (!pendingRevoke) return
    setRevoking(true); setError(''); setSuccess('')
    try {
      await api(`/api/admin/menu-permissions/${pendingRevoke.id}`, { method: 'DELETE' })
      setSuccess(`${subjectLabel(pendingRevoke.subjectType)} ${pendingRevoke.subjectRef} 的插件商店入口已撤销`)
      setPendingRevoke(null)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '撤销失败') }
    finally { setRevoking(false) }
  }

  return <div className="page">
    <header className="page-header"><div><p className="page-kicker">访问控制</p><h1>菜单权限</h1></div><button className="primary-button" onClick={() => setEditor('create')}><Plus size={17} aria-hidden="true" />新增菜单授权</button></header>
    <form className="toolbar permission-toolbar" onSubmit={search}>
      <div className="segmented" aria-label="授权对象类型">
        {(['', 'USER', 'DEPARTMENT'] as SubjectFilter[]).map((value) => <button key={value || 'ALL'} type="button" className={subjectType === value ? 'active' : ''} onClick={() => setSubjectType(value)}>{value ? subjectLabel(value) : '全部'}</button>)}
      </div>
      <div className="permission-filters"><label className="compact-field permission-search"><Search size={16} aria-hidden="true" /><input value={subjectRef} onChange={(event) => setSubjectRef(event.target.value)} placeholder="工号或部门编码" aria-label="筛选授权对象" /></label><button className="secondary-button">筛选</button><label className="inline-check"><input type="checkbox" checked={includeRevoked} onChange={(event) => setIncludeRevoked(event.target.checked)} />显示已撤销</label></div>
    </form>
    {error && <ErrorNotice message={error} />}
    {success && <SuccessNotice message={success} />}
    {page === null ? <Loading /> : page.content.length === 0 ? <Empty text="暂无菜单授权" /> : <div className="table-wrap"><table><thead><tr><th>授权对象</th><th>菜单</th><th>状态</th><th>更新时间</th><th>操作人</th><th className="actions-cell">操作</th></tr></thead><tbody>
      {page.content.map((item) => <tr key={item.id}><td><div className="subject-identity">{item.subjectType === 'USER' ? <UserRound size={17} aria-hidden="true" /> : <Building2 size={17} aria-hidden="true" />}<div><strong className="mono">{item.subjectRef}</strong><small className="table-subline">{subjectLabel(item.subjectType)}</small></div></div></td><td><div className="menu-name"><Store size={17} aria-hidden="true" /><strong>{menuLabel(item.menuKey)}</strong></div></td><td><StatusBadge value={item.enabled ? 'AUTHORIZED' : 'REVOKED'} /></td><td>{formatDate(item.updatedAt)}</td><td className="mono">{item.createdBy}</td><td className="actions-cell"><div className="row-actions"><button className="icon-button" title="编辑菜单授权" aria-label={`编辑 ${item.subjectRef} 菜单授权`} onClick={() => setEditor(item)}><PencilLine size={16} /></button>{item.enabled && <button className="icon-button icon-button--danger" title="撤销菜单授权" aria-label={`撤销 ${item.subjectRef} 菜单授权`} onClick={() => setPendingRevoke(item)}><Trash2 size={16} /></button>}</div></td></tr>)}
    </tbody></table></div>}
    {editor && <MenuPermissionEditor item={editor === 'create' ? undefined : editor} onClose={() => setEditor(null)} onSaved={(message) => { setEditor(null); setSuccess(message); void load() }} />}
    {pendingRevoke && <Modal title="撤销菜单授权" onClose={() => { if (!revoking) setPendingRevoke(null) }}><div className="confirm-dialog"><Trash2 size={22} aria-hidden="true" /><p>确认撤销 <strong className="mono">{pendingRevoke.subjectRef}</strong> 的“{menuLabel(pendingRevoke.menuKey)}”入口？</p><div className="form-actions"><button className="secondary-button" disabled={revoking} onClick={() => setPendingRevoke(null)}>取消</button><button className="danger-button" disabled={revoking} onClick={() => void revoke()}>{revoking ? '撤销中' : '确认撤销'}</button></div></div></Modal>}
  </div>
}

function MenuPermissionEditor({ item, onClose, onSaved }: { item?: MenuPermissionGrant; onClose: () => void; onSaved: (message: string) => void }) {
  const [subjectType, setSubjectType] = useState<MenuPermissionSubjectType>(item?.subjectType ?? 'USER')
  const [subjectRef, setSubjectRef] = useState(item?.subjectRef ?? '')
  const [menuKey, setMenuKey] = useState<MenuKey>(item?.menuKey ?? 'PLUGIN_MARKET')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      await api(item ? `/api/admin/menu-permissions/${item.id}` : '/api/admin/menu-permissions', {
        method: item ? 'PUT' : 'POST',
        body: JSON.stringify({ subjectType, subjectRef: subjectRef.trim(), menuKey }),
      })
      onSaved(`${subjectLabel(subjectType)} ${subjectRef.trim()} 的菜单权限已保存`)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '保存失败') }
    finally { setSaving(false) }
  }

  return <Modal title={item ? '编辑菜单授权' : '新增菜单授权'} onClose={onClose}><form className="form-grid" onSubmit={submit} aria-busy={saving}>
    {error && <div className="form-full"><ErrorNotice message={error} /></div>}
    <label>授权对象<select value={subjectType} onChange={(event) => { setSubjectType(event.target.value as MenuPermissionSubjectType); setSubjectRef('') }}><option value="USER">用户</option><option value="DEPARTMENT">部门</option></select></label>
    <label>{subjectType === 'USER' ? '用户工号' : '部门编码'}<input value={subjectRef} onChange={(event) => setSubjectRef(event.target.value)} required maxLength={subjectType === 'USER' ? 12 : 120} inputMode={subjectType === 'USER' ? 'numeric' : 'text'} pattern={subjectType === 'USER' ? '[0-9]{5,12}' : '[A-Za-z0-9._:/-]+'} /></label>
    <label className="form-full">授权菜单<select value={menuKey} onChange={(event) => setMenuKey(event.target.value as MenuKey)}><option value="PLUGIN_MARKET">Harness 插件商店</option></select></label>
    <div className="form-actions form-full"><button type="button" className="secondary-button" disabled={saving} onClick={onClose}>取消</button><button className="primary-button" disabled={saving}>{saving ? '保存中' : '保存授权'}</button></div>
  </form></Modal>
}

function subjectLabel(value: MenuPermissionSubjectType) { return value === 'USER' ? '用户' : '部门' }
function menuLabel(value: MenuKey) { return value === 'PLUGIN_MARKET' ? 'Harness 插件商店' : value }
function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) }
