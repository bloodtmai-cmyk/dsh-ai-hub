---
name: wecom-unified
description: "通过 Harness Enterprise Desktop中的企业微信官方验证插件，执行通讯录、日程、会议、消息、文档、表格、邮件、待办和微盘操作。"
---

# 企业微信办公套件

本 Skill 只通过 AI Hub 授权下发的受控 Tool 调用企业微信官方 CLI。不得用 Bash 直接执行 `wecom-cli`，不得安装 npm 包，不得猜测命令、参数或内部 ID。

## 前置状态

- 市场授权只决定插件是否下发。
- 个人企微扫码授权决定官方 CLI 是否能代表当前用户调用接口。
- 二者缺少任何一项都不能执行企微操作。

## Tool 路由

- 账号身份：`wecom_identity_call`
- 通讯录搜索：`wecom_contact_call`
- 日程、忙闲和会议室：`wecom_calendar_call`
- 在线会议与纪要：`wecom_meeting_call`
- 最近会话与主动发送消息：`wecom_message_call`
- Word 文档检索、读取与创建：`wecom_doc_call`
- 表格读取、追加与更新：`wecom_sheet_call`
- 邮件检索、读取与发送：`wecom_mail_call`
- 待办查询、创建与完成：`wecom_todo_call`
- 微盘文件检索与详情：`wecom_drive_call`

执行前必须先读取对应 reference，再把文档规定的 JSON 对象传给 Tool 的 `request_json`。不得在 `request_json` 中携带命令、可执行路径或 URL。日程时间直接使用 `YYYY-MM-DD HH:mm:ss`，禁止为了计算 Unix 时间戳调用 Bash 或 Python。

## 业务参考

- 通讯录：[references/wecomcli-contact.md](references/wecomcli-contact.md)
- 日程：[references/wecomcli-calendar.md](references/wecomcli-calendar.md)
- 会议：[references/wecomcli-meeting.md](references/wecomcli-meeting.md)
- 消息：[references/wecomcli-message.md](references/wecomcli-message.md)
- 文档与表格：[references/wecomcli-docs.md](references/wecomcli-docs.md)
- 邮件、待办与微盘：[references/wecomcli-work.md](references/wecomcli-work.md)

## 文件边界

插件不接受模型提供的本地 `file_path` 或 `content_path`，防止越过当前会话读取任意文件。需要导入、上传或发送附件时，必须等待工作台提供经过授权的附件句柄，不能自行猜测路径。

## 消息能力边界

官方 CLI 只支持查询机器人最近会话和向会话主动发送消息，不提供入站消息正文读取或 Bot 自动回复监听器。不得宣称用户发给 Bot 的消息会自动进入 Harness 对话或触发回复。

## 内部标识

`userid`、`chat_id`、`schedule_id`、`meeting_id` 等内部标识只用于 Tool 之间流转，不在最终回复中展示。面向用户只显示人名、会话名、日程主题和可读时间。
