---
name: agent-reach
description: >
  Use for internet research, public web search, web page reading, GitHub lookup,
  and public source discovery. Use the Agent Reach tools installed by the
  AI Hub-authorized client plug-in.
---

# Agent Reach

把互联网调研任务路由到当前会话中由 AI Hub 授权的搜索、网页阅读、代码检索和内容获取工具。

## 规则

1. 较大调研先调用 `agent_reach_doctor` 做一次真实连通性检查。
2. 使用 `agent_reach_search` 搜索，再用 `agent_reach_fetch` 读取关键原文。
3. 只使用 AI Hub 已授权并由 Harness 当前会话暴露的能力，不直接新增外部 MCP 地址或凭据。
4. 结论保留可复核的来源链接和时间。
5. 工具不可用时明确说明缺少的能力，不要要求用户打开配置文件或手工配置连接。
6. 不执行发帖、评论、点赞等外部写操作。
7. 不通过 Bash 检查或调用 `agent-reach`、`mcporter`、Exa 或 Jina；本插件把批准的 Exa MCP 与 Jina 路由直接注册为 Tool，不需要也不会安装这些 CLI。

## 路由

- 通用调研：`agent_reach_search` 后用 `agent_reach_fetch` 读取关键来源。
- GitHub：使用已授权的代码或仓库检索能力。
- 社区讨论：使用当前可见的公开社区搜索能力。
- 视频与播客：仅在会话中存在对应字幕或内容工具时使用。

本版本提供零 Key Exa 公网搜索和 Jina 原文读取。这里的“实装”是受管 Tool 直接连接后端，不是把完整 Agent Reach CLI 打进客户端；登录态社交平台与其他外部 MCP 不在本制品内。
