import { defineTool } from '@deepseek-ai/dsh-tools'
import { readFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { OPERATIONS, runWeCom } from './runner.mjs'

export const name = 'wecom-office-managed'
export const inject = ['tools', 'systemPrompt', 'skills']

const CLI = process.platform === 'win32' ? 'wecom-cli.exe' : 'wecom-cli'
const SKILL_FILE = fileURLToPath(new URL('./skills/wecom-unified/SKILL.md', import.meta.url))
const SKILL_DIR = dirname(SKILL_FILE)
const SKILL_CONTENT = readFileSync(SKILL_FILE, 'utf8')

const DOMAIN_GUIDANCE = Object.freeze({
  calendar: 'For create, use subject, begin_time, and end_time. Times use YYYY-MM-DD HH:mm:ss; Unix seconds are accepted and normalized by the plug-in. Do not use summary/start_time or Bash to calculate timestamps.',
  doc: 'For create, use doc_name and optional content/content_type. The common title alias is normalized, but doc_name is the official field.',
})

function registerDomainTool(ctx, domain, name, description) {
  const operations = Object.keys(OPERATIONS[domain])
  ctx.tools.register(defineTool({
    name,
    description,
    parameters: {
      operation: {
        type: 'string',
        enum: operations,
        required: true,
        description: `Fixed official WeCom operation: ${operations.join(', ')}.`,
      },
      request_json: {
        type: 'string',
        description: `JSON request object defined by the wecom-unified Skill. ${DOMAIN_GUIDANCE[domain] ?? ''} Never include a command, executable path, or URL.`,
      },
    },
    output: { schema: { type: 'string' }, render: (_args, value) => [{ type: 'text', text: value }] },
    timeoutMs: 95_000,
    isConcurrencySafe: (args) => OPERATIONS[domain][args.operation]?.writes !== true,
    async execute(args, exec) {
      return runWeCom(CLI, domain, args.operation, args.request_json, exec.signal)
    },
  }))
}

export function apply(ctx) {
  ctx.skills.register({
    name: 'wecom-unified',
    description: '通过企业微信官方 CLI 执行通讯录、日程、会议、消息、文档、表格、邮件、待办和微盘操作；调用企微工具前必须加载。',
    source: 'runtime',
    resourceBase: { kind: 'directory', path: SKILL_DIR },
    path: SKILL_FILE,
    content: SKILL_CONTENT,
  })

  ctx.systemPrompt.section({
    name: 'wecom-office',
    order: 124,
    text: 'For WeCom contacts, calendars, meetings, outbound messages, documents, sheets, mail, todo, and Drive, load the wecom-unified Skill and use only the wecom_*_call tools. Never invoke or probe wecom-cli through Bash, and never use Bash to calculate calendar timestamps: calendar tools accept YYYY-MM-DD HH:mm:ss directly. A bound WeCom account permits active API calls, but the official CLI does not expose inbound message content or run a bot auto-reply listener. Do not claim that messages sent to the bot will automatically enter this Harness conversation.',
  })

  registerDomainTool(ctx, 'identity', 'wecom_identity_call', 'Read the identity of the WeCom account currently authorized in Harness.')
  registerDomainTool(ctx, 'contact', 'wecom_contact_call', 'Search the WeCom directory through the official CLI before inviting named participants.')
  registerDomainTool(ctx, 'calendar', 'wecom_calendar_call', 'Create, list, search, inspect, update, or cancel WeCom schedules; query free time, buildings, and meeting rooms.')
  registerDomainTool(ctx, 'meeting', 'wecom_meeting_call', 'Create, list, search, inspect, update, or cancel online WeCom meetings and read meeting transcripts when authorized.')
  registerDomainTool(ctx, 'message', 'wecom_message_call', 'List the bot recent sessions or actively send a message to an authorized WeCom session. This tool does not receive inbound message content.')
  registerDomainTool(ctx, 'doc', 'wecom_doc_call', 'Search, read, create, append, overwrite, or rename WeCom Word documents through fixed official operations.')
  registerDomainTool(ctx, 'sheet', 'wecom_sheet_call', 'Read or update ranges, append rows, and create WeCom sheets through fixed official operations.')
  registerDomainTool(ctx, 'mail', 'wecom_mail_call', 'Search, read, or send WeCom mail through fixed official operations.')
  registerDomainTool(ctx, 'todo', 'wecom_todo_call', 'List, inspect, create, update, or finish WeCom todo items through fixed official operations.')
  registerDomainTool(ctx, 'drive', 'wecom_drive_call', 'List, search, or inspect files in WeCom Drive through fixed official operations. Local file paths are never accepted.')
}
