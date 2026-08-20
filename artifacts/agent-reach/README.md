# Agent Reach managed plugin

This constrained managed implementation is derived from the public ideas and workflows in [Panniantong/Agent-Reach](https://github.com/Panniantong/Agent-Reach). The upstream MIT notice is preserved in [UPSTREAM_LICENSE.md](UPSTREAM_LICENSE.md).

这是 AI Hub 企业插件市场的首个可执行受控制品：

1. 管理员上传 ZIP。
2. 制品准入并发布。
3. 用户在 Harness 企业插件市场申请，或管理员直接授权推送。
4. Harness 校验 SHA-256、解压制品、安装内含 Skill，并生成只读 Cordis Patch。
5. 运行中的 Harness 定时对账，下载变更并提示重启启用。

授权并重启后，插件会注册 `agent_reach_search`、`agent_reach_fetch` 和
`agent_reach_doctor` 三个只读 Tool。搜索使用零 Key 的 Exa 公共 MCP，原文读取使用 Jina Reader，
通过 macOS/Windows 自带的 `curl` 固定参数调用；不拼接 Shell 命令，拒绝本机和私网目标，
只连接制品清单声明的 Exa 公共 MCP，不连接其他 MCP，也不安装系统命令或读取浏览器 Cookie。
因此 `agent-reach` 或 `mcporter` 在 Bash 中显示 command not found 并不代表插件未实装；模型应直接使用上述三个受管 Tool，不能探测不存在的 CLI。

需要登录态的 Twitter、Reddit、小红书等渠道仍应作为后续独立 Provider 审核，
不能由客户端插件静默复用用户浏览器身份。
