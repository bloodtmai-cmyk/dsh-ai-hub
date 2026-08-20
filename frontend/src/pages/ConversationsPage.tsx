import { Eye, Filter, MessageSquareText, X } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { ConversationDetail, ConversationSummary, Page } from '../lib/types'

export function ConversationsPage() {
  const [page, setPage] = useState<Page<ConversationSummary> | null>(null)
  const [workcode, setWorkcode] = useState('')
  const [status, setStatus] = useState('')
  const [detail, setDetail] = useState<ConversationDetail | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    const query = new URLSearchParams({ size: '100' })
    if (workcode) query.set('workcode', workcode)
    if (status) query.set('status', status)
    try { setPage(await api<Page<ConversationSummary>>(`/api/admin/conversations?${query}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '加载失败') }
  }, [status, workcode])

  useEffect(() => { void load() }, [])
  async function filter(event: FormEvent) { event.preventDefault(); await load() }
  async function open(id: string) {
    setError('')
    try { setDetail(await api<ConversationDetail>(`/api/admin/conversations/${id}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '读取失败') }
  }

  return <div className="page page--with-drawer">
    <header className="page-header"><div><p className="page-kicker">合规审计</p><h1>对话审计</h1></div></header>
    <form className="toolbar audit-filters" onSubmit={filter}><label className="compact-field"><Filter size={16} /><input value={workcode} onChange={(event) => setWorkcode(event.target.value)} placeholder="工号" /></label><select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option><option>SUCCESS</option><option>ERROR</option><option>CANCELLED</option></select><button className="secondary-button">筛选</button></form>
    {error && <ErrorNotice message={error} />}
    {!page ? <Loading /> : page.content.length === 0 ? <Empty /> : <div className="table-wrap"><table><thead><tr><th>工号</th><th>会话 / 回合</th><th>模型</th><th>开始时间</th><th>耗时</th><th>Token</th><th>状态</th><th className="actions-cell">查看</th></tr></thead><tbody>{page.content.map((item) => <tr key={item.id}><td className="mono">{item.workcode}</td><td><strong className="mono compact-text">{item.sessionId}</strong><small className="table-subline">{item.turnId}</small></td><td>{item.model}</td><td>{formatDate(item.startedAt)}</td><td>{item.latencyMs ? `${item.latencyMs} ms` : '-'}</td><td>{item.inputTokens + item.outputTokens}</td><td><StatusBadge value={item.status} /></td><td className="actions-cell"><button className="icon-button" title="查看对话正文" onClick={() => open(item.id)}><Eye size={17} /></button></td></tr>)}</tbody></table></div>}
    {detail && <aside className="detail-drawer"><header><div><MessageSquareText size={19} /><div><h2>回合详情</h2><span>{detail.workcode} · {detail.model}</span></div></div><button className="icon-button" onClick={() => setDetail(null)} title="关闭"><X size={18} /></button></header><dl className="detail-meta"><div><dt>会话</dt><dd>{detail.sessionId}</dd></div><div><dt>回合</dt><dd>{detail.turnId}</dd></div><div><dt>客户端</dt><dd>{detail.clientInstallationId}</dd></div><div><dt>时间</dt><dd>{formatDate(detail.startedAt)}</dd></div></dl><section className="message-block"><span>USER</span><p>{detail.userMessage}</p></section><section className="message-block message-block--assistant"><span>ASSISTANT</span><p>{detail.assistantMessage}</p></section></aside>}
  </div>
}

function formatDate(value: string) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) }
