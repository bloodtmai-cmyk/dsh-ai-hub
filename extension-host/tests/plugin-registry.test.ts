import assert from 'node:assert/strict'
import test from 'node:test'
import { Context } from '@deepseek-ai/cordis'
import {
  conversationAuditPlugin,
  gatewayCatalogPlugin,
  manualKeyMaintenancePlugin,
  skillArtifactPlugin,
  workbuddyLdapAuthPlugin,
} from '../src/builtin-plugins.js'
import { HubPluginRegistry } from '../src/plugin-registry.js'

test('registers the controlled built-in plugin inventory', async () => {
  const originalURL = process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL
  const originalFetch = globalThis.fetch
  process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL = 'https://gateway.example.test/workbuddy-mcp/oauth2/identity-provider'
  globalThis.fetch = async () => new Response(JSON.stringify({
    id: 'ldap',
    interactionMode: 'PASSWORD',
    authorizationPath: '/oauth2/authorize',
  }), { status: 200, headers: { 'content-type': 'application/json' } })

  const root = new Context()
  try {
    await root.plugin(HubPluginRegistry)
    await root.plugin(gatewayCatalogPlugin)
    await root.plugin(skillArtifactPlugin)
    await root.plugin(conversationAuditPlugin)
    await root.plugin(manualKeyMaintenancePlugin)
    await root.plugin(workbuddyLdapAuthPlugin)

    const plugins = await root.hubPluginRegistry.list()
    assert.deepEqual(plugins.map((plugin) => plugin.id), [
      'conversation-audit',
      'gateway-catalog',
      'manual-key-maintenance',
      'skill-artifact',
      'workbuddy-ldap-auth',
    ])
    assert.equal(plugins.find((plugin) => plugin.id === 'manual-key-maintenance')?.state, 'READY')
    assert.equal(plugins.find((plugin) => plugin.id === 'workbuddy-ldap-auth')?.state, 'READY')

    const identityProvider = await root.hubPluginRegistry.invoke('workbuddy-ldap-auth', 'inspect', {})
    assert.deepEqual(identityProvider, {
      id: 'ldap',
      interactionMode: 'PASSWORD',
      authorizationPath: '/oauth2/authorize',
    })

    const normalized = await root.hubPluginRegistry.invoke('gateway-catalog', 'normalize', {
      capabilities: [{ type: 'mcp', externalRef: 'gateway-003', name: '库存能力', releaseVersion: '1' }],
    })
    assert.deepEqual(normalized, {
      capabilities: [{
        type: 'MCP',
        externalRef: 'gateway-003',
        name: '库存能力',
        description: '',
        parentExternalRef: undefined,
        releaseVersion: '1',
        sourceRef: undefined,
      }],
    })
  } finally {
    await root.fiber.dispose()
    globalThis.fetch = originalFetch
    if (originalURL === undefined) delete process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL
    else process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL = originalURL
  }
})

test('reports a non-LDAP WorkBuddy provider without claiming LDAP is ready', async () => {
  const originalURL = process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL
  const originalFetch = globalThis.fetch
  process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL = 'https://gateway.example.test/workbuddy-mcp/oauth2/identity-provider'
  globalThis.fetch = async () => new Response(JSON.stringify({
    id: 'e10',
    interactionMode: 'PASSWORD',
    authorizationPath: '/oauth2/authorize',
  }), { status: 200, headers: { 'content-type': 'application/json' } })

  const root = new Context()
  try {
    await root.plugin(HubPluginRegistry)
    await root.plugin(workbuddyLdapAuthPlugin)
    const [plugin] = await root.hubPluginRegistry.list()
    assert.equal(plugin?.state, 'DEGRADED')
    assert.equal(plugin?.detail, 'WorkBuddy 当前使用 e10 认证 Provider')
  } finally {
    await root.fiber.dispose()
    globalThis.fetch = originalFetch
    if (originalURL === undefined) delete process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL
    else process.env.AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL = originalURL
  }
})

test('rejects unknown plugin operations', async () => {
  const root = new Context()
  await root.plugin(HubPluginRegistry)
  await assert.rejects(() => root.hubPluginRegistry.invoke('missing-plugin', 'run', {}), /平台插件不存在/)
  await root.fiber.dispose()
})
