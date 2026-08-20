import { Puzzle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Empty, ErrorNotice, Loading } from '../components/States'
import { StatusBadge } from '../components/StatusBadge'
import { api } from '../lib/api'
import type { PlatformPluginInventory } from '../lib/types'

export function PlatformPluginsPage() {
  const [inventory, setInventory] = useState<PlatformPluginInventory | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api<PlatformPluginInventory>('/api/admin/platform-plugins')
      .then(setInventory)
      .catch((reason: Error) => setError(reason.message))
  }, [])

  return (
    <div className="page">
      <header className="page-header">
        <div><p className="page-kicker">受控扩展</p><h1>平台模块</h1></div>
        <Puzzle aria-hidden="true" />
      </header>
      {error && <ErrorNotice message={error} />}
      {!inventory ? <Loading /> : !inventory.reachable ? (
        <ErrorNotice message={inventory.message ?? 'Cordis Extension Host 不可用'} />
      ) : inventory.plugins.length === 0 ? <Empty text="没有已装配的平台模块" /> : (
        <div className="table-wrap">
          <table>
            <thead><tr><th>模块</th><th>类型</th><th>提供能力</th><th>权限声明</th><th>版本</th><th>状态</th></tr></thead>
            <tbody>{inventory.plugins.map((plugin) => (
              <tr key={plugin.id}>
                <td><strong>{plugin.displayName}</strong><span className="table-subline mono">{plugin.id}</span><span className="table-subline">{plugin.description}</span></td>
                <td>{plugin.kind === 'PROVIDER' ? 'Provider' : 'Connector'}</td>
                <td><span className="compact-text" title={plugin.provides.join(', ')}>{plugin.provides.join(', ')}</span></td>
                <td><span className="compact-text" title={plugin.permissions.join(', ')}>{plugin.permissions.join(', ') || '无'}</span></td>
                <td className="mono">{plugin.version}</td>
                <td><StatusBadge value={plugin.state} />{plugin.detail && <span className="table-subline">{plugin.detail}</span>}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </div>
  )
}
