# DSH AI Hub 首期架构

## 范围

首期只建设管理与授权控制面：管理员登录、对话审计、MCP/Tool/Skill/Bundle/CLIENT_PLUGIN 接入准入和授权、企业指令全员发布、菜单权限、企业市场申请审批、API Key 申请审批与领取。AI Hub 不代理 MCP Tool 调用，不保存 Gateway 请求头或下游业务凭据，也不判断组织、仓库、项目、物料等业务数据范围。

## 身份边界

- 管理端使用独立的服务端 Session。只有配置的单一管理员工号可以认证，所有写操作要求 CSRF Token。
- Harness 客户端接口使用短期 Bearer JWT。生产通过受信 Gateway 的 JWKS 验证签名、issuer 和 audience，后端只信任验签后 JWT 的 `workcode` 与 `department_codes` claims，不接受请求体或查询参数覆盖身份。
- Gateway 内部接口使用独立服务身份。Gateway 只负责发现和执行，不能自行决定能力是否对用户暴露。
- 对话审计服务接入与普通用户请求使用同一用户 JWT；后续批量服务接入可增加独立服务身份，但不能退化为裸 `X-Workcode`。
- 本地 `dev` profile 提供 JWT 生成入口，只用于开发验证；生产 profile 不装配该控制器。

## 数据保护

- 对话正文与待领取 API Key 使用 AES-256-GCM 加密后入库，不记录思考过程、文件正文和 Tool 完整参数。
- API Key 领取为一次性操作。领取后数据库保留加密密文、哈希和掩码，业务接口不再解密返回。
- 每次管理员查看对话正文、审批/拒绝/吊销 Key、发布能力和修改授权都会写入不可变管理审计日志。
- 生产必须从 Secret 注入管理员密码哈希、JWT 验签密钥、内容加密主密钥和数据库密码。

## Cordis 扩展边界

AI Hub 采用双运行时结构：Spring Core 是安全、权限与数据事实来源；Cordis Extension
Host 只承载受控的外部 Provider/Connector。Extension Host 固定监听
`127.0.0.1:8091`，Spring 使用独立 Bearer 服务身份调用，生产启动时要求宿主和指定内置
插件全部就绪。

```text
Nginx -> Spring Core 8090 -> PostgreSQL
                    |
                    +-> Cordis Extension Host 8091
                              +-> Gateway Catalog Connector
                              +-> Skill Artifact Provider
                              +-> Conversation Audit Connector
                              +-> API Key 手工维护契约
                              +-> WorkBuddy LDAP/AD 状态 Provider
```

管理员认证、JWT 验签、授权决策、能力状态机、Bundle 展开、对话加密存储、管理审计、
Key 审批与一次性领取、Flyway 迁移始终留在 Core。Extension Host 不直接写 AI Hub 数据库，
也不参与 Gateway 的 `LIST`/`CALL` 最终授权决策。宿主不可用时不得降级绕过权限。

平台插件 Manifest 声明 ID、版本、API 版本、依赖、提供能力、权限、Secret 和 UI Slot。
首期只装配随 AI Hub 构建的内置插件，不启用 Loader/HMR，不接收 npm、Git URL 或上传代码。
Cordis Fiber 负责插件依赖与资源释放，但 Cordis 和 `node:vm` 都不被视为安全沙箱。

`workbuddy-ldap-auth` 是外部认证 Provider 的只读状态桥，不执行认证，也不接收 LDAP
凭据。它从 `AI_HUB_WORKBUDDY_IDENTITY_PROVIDER_URL` 读取 WorkBuddy 当前 Provider 元数据；
未单独配置时使用 `${AI_HUB_JWT_ISSUER}/oauth2/identity-provider`。只有 WorkBuddy 当前选择
`ldap` 且使用受控密码票据模式时显示就绪，其他 Provider、不可达或无效配置均显示待配置。

React 管理端使用编译期 UI Slot 注册表。插件页面代码随前端构建，运行时只读取宿主清单与
健康状态，不下载或执行远程 JavaScript。商店中的 MCP、Tool、Skill、Bundle、CLIENT_PLUGIN 是用户能力，
与扩展 AI Hub 代码的平台插件严格分离。

## 能力授权

```text
Gateway 同步 MCP/Tool + 管理员上传 Skill/客户端插件
  -> capability(MCP|SKILL|BUNDLE|CLIENT_PLUGIN): DISCOVERED
  -> 管理员准入: APPROVED
  -> 管理员发布: PUBLISHED
  -> subject_grant(workcode)
  -> Harness 目录 + Gateway tools/list/tools/call 授权检查

Tool: 随所属 MCP 准入/发布 + subject_tool_exclusion(workcode, tool)
```

目录默认拒绝。只有 `PUBLISHED` 能力且存在当前有效、未撤回的工号授权时，才进入用户目录。Tool 是 MCP 的子资源，不单独准入、发布或授权：MCP 发布时级联发布已发现 Tool，发布后的 MCP 新发现 Tool 自动发布；用户获得 MCP 后默认开放全部已发布 Tool，再由 `subject_tool_exclusion` 为特定用户取消个别 Tool。Gateway 在 `tools/list` 和 `tools/call` 两个阶段通过内部检查接口或短时授权快照求交；客户端目录不是安全事实来源。

Skill 不再从 GitLab 拉取。管理员在 AI Hub 上传单个 Markdown 或包含一个顶层 `SKILL.md` 的 ZIP，平台完成大小、路径、结构和完整性校验后进入 `DISCOVERED`。审批发布后 Harness 按用户目录下载并校验 SHA-256。`BUNDLE` 是授权聚合能力：可以组合 MCP、Skill 和客户端插件，不直接包含 Tool；Tool 随成员 MCP 展开。用户获得 Bundle 授权后，目录按成员展开，Tool 调用仍由 Gateway 直连执行。

`CLIENT_PLUGIN` 由管理员上传 ZIP，顶层必须包含 `plugin.json`，并声明包内存在的 `.mjs` 入口和 `activation=hot`。Hub 校验上传与解压大小、条目数量、安全路径、清单字段和 SHA-256；Harness 仅下载当前工号已授权版本，再次校验哈希、有界解压并生成受管 Cordis Patch。市场申请批准后生成普通用户授权，管理员直接新增授权就是主动推送。发布同一稳定标识的新版本时必须高于当前 SemVer，Hub 在同一事务内把有效用户授权和 Bundle 成员引用迁移到新能力，再停用旧能力，用户不需要重新申请。Harness 运行中定时对账，原子替换插件目录与 Patch；Host 只监听这一份受管 Patch，以完整 overlay 事务重组 Cordis 生命周期并回写对应 SHA-256。激活失败时 Electron 恢复上一份健康 generation，并安排下次启动重试，不向用户循环弹出重启提示。任意目录模块监听、在线 npm/Git 安装和插件自更新仍保持禁用。

首个企业视觉制品源码位于 `artifacts/vision-toolkit`。它从社区 `dsh-vision-toolkit` 只吸收工作区图片检查、裁剪、像素差异、主色和视觉理解流程；语义视觉只能复用 Harness 从 Hub 领取的模型网关地址、Key 与默认模型，不允许用户覆盖，也不包含外部共享视觉服务、自更新、TLS 绕过或运行期 Python 下载。`DSH-better-sidebar` 不作为市场插件发布，只把第三列页签与路由内容插槽吸收到 Harness 核心，排除其 PTY、浏览器、Git 和插件安装权限。

企业指令不复用用户授权。管理员上传固定文件名 `AGENTS.md`，准入后以语义版本发布；同一稳定标识只允许更高版本原子覆盖，当前已发布版本不允许停用或撤销。Harness 总是获取当前全员版本并在下一次模型调用时读取最新只读副本，因此指令变更不要求重启。

菜单权限与能力授权分离，使用 `menu_permission_grant` 记录 `USER` 或 `DEPARTMENT` 对菜单键的访问。客户端以受信 JWT 的 `workcode` 和 `department_codes` 求并集；当前 `PLUGIN_MARKET` 同时控制 Harness 左侧菜单可见性和市场 API 访问，不能只靠前端隐藏。

## 客户端升级

客户端版本不属于能力目录，也不参与用户授权。管理员在独立菜单按 `MAC_ARM64`、`MAC_X64` 或 `WINDOWS_X64` 上传 `.dmg`、`.pkg` 或 `.exe` 草稿，AI Hub 流式写入受控制品目录并记录大小、SHA-256 与 electron-updater 所需的 SHA-512。发布只接受高于该平台当前版本的 SemVer；数据库约束保证每个平台至多一个 `PUBLISHED` 版本，旧版本转为 `SUPERSEDED` 后仍可短期下载，避免已开始升级的客户端因切版中断。

Harness 使用当前 WorkBuddy JWT 检查更新。macOS 查询高于 `app.getVersion()` 的版本，用户确认后把制品写入临时文件并校验大小和 SHA-256，原子落盘后再次确认，再通过 Electron `shell.openPath` 交给操作系统。Windows 通过鉴权 Generic Provider 获取 `latest.yml` 和 NSIS 制品，由 `electron-updater` 做 SHA-512 校验，并在二次确认后执行静默原位覆盖和重启。下载、校验或安装失败都保留当前安装；当前未发布 blockmap，差分下载保持禁用。强制升级、灰度策略和企业签名链待正式发布策略确定后接入。

## API Key 生命周期

```text
用户申请 -> PENDING -> 管理员录入已有 Key 并批准 -> 加密保存 -> 一次性领取 -> CLAIMED
                           |                         |
                           +-> 拒绝                  +-> 管理员吊销
```

Hub 不依赖特定模型平台的管理 API。管理员在上游 Provider 创建 Key，再在审批界面录入
Provider 标识、OpenAI 兼容网关地址和 Key；Spring Core 保存手工绑定标识、网关元数据、掩码、
SHA-256 哈希、加密密文、状态和领取时间。Hub 内标记吊销后不再对客户端提供该 Key，但管理员
仍需在上游 Provider 同步停用。LiteLLM 可作为网关实现之一。

## 后续接入点

- Gateway MCP/Tool 生产配置同步与重试告警。
- Skill 审批意见、版本回滚和历史制品保留策略。
- WorkBuddy JWT 的企业 JWKS/issuer/audience 配置。
- 对话审计脱敏策略、留存删除任务和访问审批。
