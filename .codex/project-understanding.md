# 项目理解

## 项目定位

- DSH AI Hub 是面向 DeepSeek Harness 受管部署的独立开源控制面，不代理 MCP Tool 正常调用流量，也不接管业务系统自身的数据权限。
- 对外定位保持克制：它服务于企业内部统一智能工作台入口，不替代身份提供方、MDM、业务数据代理或下游授权系统。
- Core 负责身份验签、能力状态机、授权、审计、模型访问配置一次性领取、客户端发布、加密与数据库迁移；Cordis Extension Host 只承载受信 Provider/Connector。
- MCP、Tool、Skill、Bundle 与客户端插件统一进入能力目录。Tool 从属于 MCP，随 MCP 准入和授权，用户可在 MCP 详情中排除单个 Tool。
- 企业托管指令是只读、版本化、全员生效的 `AGENTS.md`，不进入用户市场或逐用户授权。

## 安全边界

- 管理 API 使用 Session 与 CSRF；客户端 API 使用受信 Gateway 签发的 Bearer JWT。`workcode` 与部门 Claim 只能来自验签后的 Token。
- 对话正文和待领取模型 API Key 使用 AES-256-GCM 加密。API Key 只允许领取一次；Provider 标识和 OpenAI 兼容网关地址由 Hub 持续下发，模型、预算和有效期由上游 Provider 管理。LiteLLM 只是可选实现。
- V9 之前的旧 Key 绑定没有 Provider 与网关地址，迁移时不猜测默认地址；领取和客户端复用均默认拒绝，管理员需要重新签发完整授权。
- Gateway 在 `tools/list` 和 `tools/call` 两阶段校验 Hub 授权；Hub 不执行 Tool，也不接受客户端提供的身份覆盖。
- 平台插件随受信构建固定装配；客户端插件必须经过制品校验、审批、版本治理和用户授权，不能从任意 URL、Git 仓库或本地 ZIP 直接安装。
- 社区仓库不得包含真实域名、IP、组织名称、工号、凭据、数据库连接或专有业务接口。示例只使用 `example.com`、回环地址和显式占位值。

## 运行方式

- 后端位于 `backend`，使用 Java 21、Spring Boot、Spring Security、JPA 与 Flyway，默认监听 `127.0.0.1:8090`。
- Extension Host 位于 `extension-host`，使用 Node.js 22+ 与 Cordis，默认监听 `127.0.0.1:8091`。
- 前端位于 `frontend`，使用 React、TypeScript 与 Vite，开发服务器默认监听 `127.0.0.1:4173`。
- 默认 local profile 使用文件型 H2；PostgreSQL 示例连接本机 `ai_hub` 数据库与独立 `ai_hub` schema，生产配置全部通过环境变量或 Secret 注入。
- 示例 systemd 部署目录为 `/opt/dsh-ai-hub`，示例 Nginx 只反向代理本机 Core。生产必须使用 HTTPS 并保持 Session Cookie Secure。

## 开源约定

- Java 根包为 `ai.dsh.hub`，Maven groupId 为 `ai.dsh`，前端与 Extension Host npm scope 为 `@dsh`。
- 产品名称使用 `DSH AI Hub`；界面使用通用能力治理标识，不携带任何组织 Logo。
- `artifacts/` 只保留可公开审查的通用示例。组织专有 HR、WMS、OA 等连接器不得进入社区分支。
- 许可证为 MIT。提交社区仓库前必须运行后端测试、Extension Host 测试与构建、前端构建以及脱敏扫描。
- 公开仓库只发布源码，不发布桌面安装包；维护者与个人版权主体为 `clanie`，安全联系邮箱为 `bloodtmai@gmail.com`。
- Agent Reach、Vision Toolkit 和 WeCom CLI 相关公开来源均为 MIT，仓库必须保留其上游署名并说明外部服务条款。

## 公开发布

- 公开 namespace 为 `bloodtmai-cmyk/dsh-ai-hub`，内容从审查后的索引导出到全新仓库，不携带内部 Git 历史。
- CI 对后端、前端、Extension Host 和公开制品执行测试，并生成 CycloneDX SBOM；最终锁文件发布前执行漏洞扫描。

## 待验证

- 社区版受信 Gateway 的参考实现与标准 JWT Claim 契约。
- 是否需要单独的贡献者协议。
