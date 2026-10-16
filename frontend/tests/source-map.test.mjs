import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { spawnSync } from 'node:child_process'
import test from 'node:test'

const require = createRequire(import.meta.url)
const requirePostcss = createRequire(require.resolve('postcss'))
const { SourceMapConsumer, SourceMapGenerator } = requirePostcss('source-map-js')

test('normal source maps preserve positions and source content', () => {
  const generator = new SourceMapGenerator({ file: 'output.css' })
  generator.addMapping({ generated: { line: 1, column: 0 }, original: { line: 2, column: 3 }, source: 'input.css' })
  generator.setSourceContent('input.css', '\n   a { color: red; }')
  const consumer = new SourceMapConsumer(generator.toJSON())
  assert.deepEqual(consumer.originalPositionFor({ line: 1, column: 0 }), { source: 'input.css', line: 2, column: 3, name: null })
  const roundTrip = new SourceMapConsumer(SourceMapGenerator.fromSourceMap(consumer).toJSON())
  assert.equal(roundTrip.sourceContentFor('input.css'), '\n   a { color: red; }')
  assert.deepEqual(roundTrip.generatedPositionFor({ source: 'input.css', line: 2, column: 3 }), { line: 1, column: 0, lastColumn: null })
})

test('PostCSS produces usable source maps', async () => {
  const postcss = require('postcss')
  const result = await postcss([{
    postcssPlugin: 'source-map-compatibility-test',
    Declaration(declaration) { if (declaration.prop === 'color') declaration.value = 'blue' },
  }]).process('a { color: red; }', { from: '/fixture/input.css', to: '/fixture/output.css', map: { inline: false } })
  assert.match(result.css, /color: blue/)
  const consumer = new SourceMapConsumer(result.map.toJSON())
  const position = consumer.originalPositionFor({ line: 1, column: result.css.indexOf('color') })
  assert.equal(position.source, 'input.css')
  assert.equal(position.line, 1)
  assert.equal(consumer.sourceContentFor('input.css'), 'a { color: red; }')
})

test('indexed source maps reject malicious offsets without blocking', () => {
  // 同步拒绝服务回归必须隔离，主进程超时后可终止子进程。
  const result = spawnSync(process.execPath, ['-e', `
    const assert = require('node:assert/strict');
    const { SourceMapConsumer, SourceNode } = require(process.argv[1]);
    const flat = { version: 3, sources: ['input.css'], sourcesContent: ['a'], names: [], mappings: 'AAAA' };
    const indexed = (line, column = 0, map = flat) => ({ version: 3, sections: [{ offset: { line, column }, map }] });
    for (const value of [-1, 1.5, NaN, Infinity, -Infinity, Number.MAX_SAFE_INTEGER + 1, '1', null, undefined, {}]) {
      for (const field of ['line', 'column']) {
        const map = indexed(0);
        map.sections[0].offset[field] = value;
        assert.throws(() => new SourceMapConsumer(map), /non-negative integers/);
      }
    }
    assert.throws(() => new SourceMapConsumer(indexed(10000001)), /must not exceed/);
    assert.throws(() => new SourceMapConsumer(indexed(5000000, 0, indexed(5000001))), /including offsets of nested sections/);
    const valid = new SourceMapConsumer(indexed(3, 4));
    assert.deepEqual(valid.originalPositionFor({ line: 4, column: 5 }), { source: 'input.css', line: 1, column: 0, name: null });
    const distant = new SourceMapConsumer(indexed(10000000));
    assert.equal(SourceNode.fromStringWithSourceMap('a', distant).toString(), 'a');
  `, requirePostcss.resolve('source-map-js')], { timeout: 5000, maxBuffer: 16384, encoding: 'utf8' })
  assert.ifError(result.error)
  assert.equal(result.signal, null)
  assert.equal(result.status, 0, result.stderr)
})
