import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const plugin = new URL('../plugin.json', import.meta.url)
const entry = new URL('../index.mjs', import.meta.url)
const skill = new URL('../skills/agent-reach/SKILL.md', import.meta.url)

test('publishes the managed Exa and Jina runtime without claiming a CLI dependency', async () => {
  const manifest = JSON.parse(await readFile(plugin, 'utf8'))
  const entryText = await readFile(entry, 'utf8')
  const skillText = await readFile(skill, 'utf8')

  assert.equal(manifest.version, '1.5.0-community.4')
  assert.match(manifest.description, /Exa MCP/)
  assert.match(entryText, /https:\/\/mcp\.exa\.ai\/mcp/)
  assert.match(entryText, /https:\/\/r\.jina\.ai\//)
  assert.match(entryText, /Never run or probe agent-reach/)
  assert.match(skillText, /不需要也不会安装这些 CLI/)
})
