import { lstat, stat, realpath } from 'node:fs/promises'
import { dirname, extname, relative, resolve } from 'node:path'
import { defineTool } from '@deepseek-ai/dsh-tools'
import sharp from 'sharp'

export const name = 'vision-toolkit-managed'
export const inject = ['tools', 'systemPrompt']

const MAX_SOURCE_BYTES = 20 * 1024 * 1024
const MAX_PIXELS = 16 * 1024 * 1024
const MAX_MODEL_RESPONSE_BYTES = 4 * 1024 * 1024

function within(root, target) {
  const path = relative(root, target)
  return path === '' || (!path.startsWith('..') && !path.startsWith('/') && !path.startsWith('\\'))
}

async function workspaceRoot() {
  return realpath(process.cwd())
}

async function sourcePath(input) {
  if (typeof input !== 'string' || input.trim().length === 0 || input.includes('\0')) {
    throw new Error('path must be a non-empty workspace-relative image path')
  }
  const root = await workspaceRoot()
  const target = await realpath(resolve(root, input))
  if (!within(root, target)) throw new Error('image path is outside the current workspace')
  const info = await stat(target)
  if (!info.isFile()) throw new Error('image path must reference a file')
  if (info.size === 0 || info.size > MAX_SOURCE_BYTES) throw new Error('image must be between 1 byte and 20 MiB')
  return { root, target, size: info.size }
}

async function outputPath(input, fallback, root) {
  const target = resolve(root, typeof input === 'string' && input.trim().length > 0 ? input : fallback)
  const parent = await realpath(dirname(target))
  if (!within(root, target) || !within(root, parent)) throw new Error('output_path is outside the current workspace')
  try {
    await lstat(target)
    throw new Error('output_path already exists; refusing to overwrite a workspace file or symlink')
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('output_path already exists')) throw error
    if (error?.code !== 'ENOENT') throw error
  }
  return target
}

function boundedDimensions(metadata) {
  const width = metadata.width ?? 0
  const height = metadata.height ?? 0
  if (width < 1 || height < 1 || width * height > MAX_PIXELS) {
    throw new Error('decoded image dimensions exceed the 16 megapixel limit')
  }
  return { width, height }
}

async function inspectImage(path) {
  const metadata = await sharp(path, { failOn: 'error', limitInputPixels: MAX_PIXELS }).metadata()
  const dimensions = boundedDimensions(metadata)
  return {
    ...dimensions,
    format: metadata.format,
    space: metadata.space,
    channels: metadata.channels,
    depth: metadata.depth,
    hasAlpha: metadata.hasAlpha,
    orientation: metadata.orientation,
  }
}

function json(value) {
  return JSON.stringify(value, null, 2)
}

function textOutput() {
  return { schema: { type: 'string' }, render: (_args, value) => [{ type: 'text', text: value }] }
}

function positiveInteger(value, name) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`)
  return value
}

function mimeType(format) {
  const formats = { jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp', gif: 'image/gif', tiff: 'image/tiff', avif: 'image/avif' }
  const value = formats[format]
  if (value === undefined) throw new Error(`managed vision does not support the ${format ?? 'unknown'} image format`)
  return value
}

async function visionModel(path, operation, prompt, signal) {
  const baseURL = process.env.MODEL_GATEWAY_BASE_URL?.trim() || process.env.LITELLM_BASE_URL?.trim()
  const apiKey = process.env.MODEL_GATEWAY_API_KEY?.trim() || process.env.LITELLM_API_KEY?.trim()
  const model = process.env.MODEL_GATEWAY_DEFAULT_MODEL?.trim() || process.env.LITELLM_DEFAULT_MODEL?.trim()
  if (!baseURL || !apiKey || !model) throw new Error('managed model gateway vision configuration is unavailable')
  const url = new URL(`${baseURL.replace(/\/+$/, '')}/chat/completions`)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('managed model gateway URL must use HTTP(S)')
  const metadata = await sharp(path, { failOn: 'error', limitInputPixels: MAX_PIXELS }).metadata()
  boundedDimensions(metadata)
  const image = await sharp(path, { failOn: 'error', limitInputPixels: MAX_PIXELS }).rotate().png().toBuffer()
  const instructions = {
    describe: 'Describe the image accurately. Distinguish visible facts from uncertainty.',
    ocr: 'Extract all readable text, preserving logical reading order and table structure.',
    detect: 'List visible objects with concise locations and confidence expressed as low, medium, or high.',
    ground: 'Locate the object or text named by the user and describe its bounding area using normalized 0..1000 coordinates.',
  }
  const userPrompt = typeof prompt === 'string' ? prompt.trim().slice(0, 2000) : ''
  const body = JSON.stringify({
    model,
    max_tokens: 2000,
    messages: [{ role: 'user', content: [
      { type: 'text', text: `${instructions[operation]}${userPrompt ? `\nUser focus: ${userPrompt}` : ''}` },
      { type: 'image_url', image_url: { url: `data:${mimeType('png')};base64,${image.toString('base64')}` } },
    ] }],
  })
  const requestSignal = typeof AbortSignal.any === 'function'
    ? AbortSignal.any([signal, AbortSignal.timeout(90_000)])
    : signal
  const response = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body,
    signal: requestSignal,
  })
  const bytes = new Uint8Array(await response.arrayBuffer())
  if (bytes.byteLength > MAX_MODEL_RESPONSE_BYTES) throw new Error('model gateway vision response exceeds 4 MiB')
  const raw = Buffer.from(bytes).toString('utf8')
  let payload
  try { payload = JSON.parse(raw) } catch (error) { throw new Error('model gateway vision returned invalid JSON', { cause: error }) }
  if (!response.ok) throw new Error(`model gateway vision request failed: ${payload?.error?.message ?? response.status}`)
  const content = payload?.choices?.[0]?.message?.content
  if (typeof content !== 'string' || content.trim().length === 0) {
    throw new Error('model gateway returned no text; the administrator may need to select a vision-capable model')
  }
  return content.slice(0, 60_000)
}

export function apply(ctx) {
  ctx.systemPrompt.section({
    name: 'vision-toolkit',
    order: 127,
    text: 'Use the managed vision_* tools for workspace images. Prefer deterministic inspect, crop, compare, and palette tools before model-based vision_understand. Never install Python packages, call an external vision service, disable TLS checks, or ask the user for a model endpoint or API key.',
  })

  ctx.tools.register(defineTool({
    name: 'vision_inspect',
    description: 'Inspect dimensions, format, color space, channels, alpha, and orientation of one workspace image.',
    parameters: { path: { type: 'string', required: true, description: 'Workspace-relative image path.' } },
    output: textOutput(), timeoutMs: 20_000, isConcurrencySafe: () => true,
    async execute(args) {
      const source = await sourcePath(args.path)
      return json({ path: source.target, bytes: source.size, ...await inspectImage(source.target) })
    },
  }))

  ctx.tools.register(defineTool({
    name: 'vision_crop',
    description: 'Create a cropped PNG derived from one workspace image. The output remains inside the workspace.',
    parameters: {
      path: { type: 'string', required: true, description: 'Workspace-relative source image path.' },
      left: { type: 'number', required: true }, top: { type: 'number', required: true },
      width: { type: 'number', required: true }, height: { type: 'number', required: true },
      output_path: { type: 'string', description: 'Optional workspace-relative output path in an existing directory.' },
    },
    output: textOutput(), timeoutMs: 30_000, isConcurrencySafe: () => true,
    async execute(args) {
      const source = await sourcePath(args.path)
      const metadata = await inspectImage(source.target)
      const left = positiveInteger(args.left, 'left')
      const top = positiveInteger(args.top, 'top')
      const width = positiveInteger(args.width, 'width')
      const height = positiveInteger(args.height, 'height')
      if (width < 1 || height < 1 || left + width > metadata.width || top + height > metadata.height) {
        throw new Error('crop rectangle must stay within the source image')
      }
      const extension = extname(source.target)
      const fallback = `${extension.length > 0 ? source.target.slice(0, -extension.length) : source.target}.crop.png`
      const output = await outputPath(args.output_path, fallback, source.root)
      if (output === source.target) throw new Error('output_path must not overwrite the source image')
      await sharp(source.target, { failOn: 'error', limitInputPixels: MAX_PIXELS })
        .extract({ left, top, width, height }).png().toFile(output)
      return json({ path: output, ...await inspectImage(output) })
    },
  }))

  ctx.tools.register(defineTool({
    name: 'vision_compare',
    description: 'Compare two equal-size workspace images pixel by pixel and return bounded difference metrics.',
    parameters: {
      left_path: { type: 'string', required: true },
      right_path: { type: 'string', required: true },
    },
    output: textOutput(), timeoutMs: 30_000, isConcurrencySafe: () => true,
    async execute(args) {
      const [left, right] = await Promise.all([sourcePath(args.left_path), sourcePath(args.right_path)])
      const [a, b] = await Promise.all([
        sharp(left.target, { failOn: 'error', limitInputPixels: MAX_PIXELS }).rotate().ensureAlpha().raw().toBuffer({ resolveWithObject: true }),
        sharp(right.target, { failOn: 'error', limitInputPixels: MAX_PIXELS }).rotate().ensureAlpha().raw().toBuffer({ resolveWithObject: true }),
      ])
      if (a.info.width !== b.info.width || a.info.height !== b.info.height || a.info.channels !== b.info.channels) {
        throw new Error('vision_compare requires images with equal decoded dimensions')
      }
      if (a.info.width * a.info.height > MAX_PIXELS) throw new Error('decoded image dimensions exceed the 16 megapixel limit')
      let changedPixels = 0
      let totalDelta = 0
      let maxDelta = 0
      for (let index = 0; index < a.data.length; index += a.info.channels) {
        let pixelChanged = false
        for (let channel = 0; channel < a.info.channels; channel++) {
          const delta = Math.abs(a.data[index + channel] - b.data[index + channel])
          totalDelta += delta
          maxDelta = Math.max(maxDelta, delta)
          if (delta > 0) pixelChanged = true
        }
        if (pixelChanged) changedPixels++
      }
      const pixels = a.info.width * a.info.height
      return json({ width: a.info.width, height: a.info.height, changedPixels,
        changedRatio: changedPixels / pixels, meanAbsoluteChannelDelta: totalDelta / a.data.length, maxChannelDelta: maxDelta })
    },
  }))

  ctx.tools.register(defineTool({
    name: 'vision_palette',
    description: 'Extract a deterministic approximate dominant-color palette from one workspace image.',
    parameters: { path: { type: 'string', required: true }, colors: { type: 'number', description: 'Number of colors, 1..12.' } },
    output: textOutput(), timeoutMs: 20_000, isConcurrencySafe: () => true,
    async execute(args) {
      const source = await sourcePath(args.path)
      const count = args.colors === undefined ? 6 : positiveInteger(args.colors, 'colors')
      if (count < 1 || count > 12) throw new Error('colors must be between 1 and 12')
      const { data, info } = await sharp(source.target, { failOn: 'error', limitInputPixels: MAX_PIXELS })
        .rotate().removeAlpha().resize(64, 64, { fit: 'inside', withoutEnlargement: true }).raw().toBuffer({ resolveWithObject: true })
      const bins = new Map()
      for (let index = 0; index < data.length; index += info.channels) {
        const rgb = [data[index], data[index + 1], data[index + 2]].map(value => Math.min(255, Math.round(value / 32) * 32))
        const key = rgb.join(',')
        bins.set(key, (bins.get(key) ?? 0) + 1)
      }
      const palette = [...bins.entries()].sort((a, b) => b[1] - a[1]).slice(0, count)
        .map(([key, samples]) => ({ hex: `#${key.split(',').map(value => Number(value).toString(16).padStart(2, '0')).join('')}`, samples }))
      return json({ path: source.target, palette })
    },
  }))

  ctx.tools.register(defineTool({
    name: 'vision_understand',
    description: 'Use the administrator-managed model gateway for image description, OCR, detection, or grounding.',
    parameters: {
      path: { type: 'string', required: true },
      operation: { type: 'string', required: true, enum: ['describe', 'ocr', 'detect', 'ground'] },
      prompt: { type: 'string', description: 'Optional focus, up to 2000 characters. Endpoint, model, and credentials cannot be overridden.' },
    },
    output: textOutput(), timeoutMs: 95_000, isConcurrencySafe: () => true,
    async execute(args, exec) {
      const source = await sourcePath(args.path)
      return visionModel(source.target, args.operation, args.prompt, exec.signal)
    },
  }))
}
