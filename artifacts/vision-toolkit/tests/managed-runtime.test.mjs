import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const plugin = new URL('../plugin.json', import.meta.url)
const entry = new URL('../index.mjs', import.meta.url)
const skill = new URL('../skills/vision-toolkit/SKILL.md', import.meta.url)

test('publishes a hot, enterprise-managed vision runtime', async () => {
  const manifest = JSON.parse(await readFile(plugin, 'utf8'))
  const source = await readFile(entry, 'utf8')
  const skillText = await readFile(skill, 'utf8')

  assert.equal(manifest.activation, 'hot')
  assert.deepEqual(manifest.permissions, [
    'filesystem:workspace-read', 'filesystem:workspace-write-derived', 'model:managed-gateway',
  ])
  for (const tool of ['vision_inspect', 'vision_crop', 'vision_compare', 'vision_palette', 'vision_understand']) {
    assert.match(source, new RegExp(`name: '${tool}'`))
  }
  assert.match(source, /realpath\(process\.cwd\(\)\)/)
  assert.match(source, /refusing to overwrite a workspace file or symlink/)
  assert.match(source, /process\.env\.MODEL_GATEWAY_API_KEY/)
  assert.match(skillText, /不要求用户填写 API Key/)
  assert.doesNotMatch(source, /vision\.anionex\.me|rejectUnauthorized|NODE_TLS_REJECT_UNAUTHORIZED|pip install|child_process/)
})
