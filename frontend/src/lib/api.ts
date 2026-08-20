export type ApiProblem = { code: string; message: string }

let csrfToken: string | null = null
const basePath = import.meta.env.BASE_URL.replace(/\/$/, '')

function hubPath(path: string): string {
  return `${basePath}${path}`
}

export function hubUrl(path: string): string {
  return hubPath(path)
}

export async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken
  const response = await fetch(hubPath('/api/admin/csrf'), { credentials: 'include' })
  if (!response.ok) throw new Error('无法建立安全会话')
  const body = (await response.json()) as { token: string }
  csrfToken = body.token
  return body.token
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-XSRF-TOKEN', await ensureCsrf())
  }
  const response = await fetch(hubPath(path), { ...init, headers, credentials: 'include' })
  if (!response.ok) {
    let problem: ApiProblem = { code: 'REQUEST_FAILED', message: `请求失败 (${response.status})` }
    try {
      problem = (await response.json()) as ApiProblem
    } catch {
      // Non-JSON proxy errors keep the status-based message.
    }
    throw new Error(problem.message)
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  return (await response.json()) as T
}

export async function upload<T>(path: string, body: FormData, onProgress: (percent: number) => void): Promise<T> {
  const token = await ensureCsrf()
  return await new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', hubPath(path))
    request.withCredentials = true
    request.setRequestHeader('X-XSRF-TOKEN', token)
    request.responseType = 'json'
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && event.total > 0) onProgress(Math.round(event.loaded / event.total * 100))
    })
    request.addEventListener('load', () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(100)
        resolve(request.response as T)
        return
      }
      const problem = request.response as ApiProblem | null
      reject(new Error(problem?.message ?? `请求失败 (${request.status})`))
    })
    request.addEventListener('error', () => reject(new Error('上传连接失败')))
    request.addEventListener('abort', () => reject(new Error('上传已取消')))
    request.send(body)
  })
}

export function clearCsrf() {
  csrfToken = null
}
