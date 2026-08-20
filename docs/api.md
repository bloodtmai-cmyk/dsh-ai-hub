# 首期 API 接入

## 鉴权

| 接口范围 | 身份 |
|---|---|
| `/api/admin/**` | 管理员 Session + `X-XSRF-TOKEN` |
| `/api/client/**` | `Authorization: Bearer <user-jwt>` |
| `/api/internal/**` | `Authorization: Bearer <gateway-service-key>` |

生产用户 JWT 通过 Gateway JWKS 验证签名、issuer 和 audience，并使用 `workcode` claim。内部服务 Key 与用户 JWT 不能混用。

## 平台插件清单

```http
GET /api/admin/platform-plugins
```

返回 Cordis Extension Host 的连通状态以及受控内置插件的 Manifest、权限声明、UI Slot
和健康状态。接口不返回 Secret，也不提供在线安装、卸载或执行任意代码的能力。

## 本地用户 Token

仅 `local` profile 可用：

```bash
curl -X POST http://127.0.0.1:8090/api/dev/tokens/12345
```

## 对话审计接入

```bash
curl -X POST http://127.0.0.1:8090/api/client/conversation-audits \
  -H "Authorization: Bearer <user-jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-001",
    "turnId": "turn-001",
    "clientInstallationId": "desktop-001",
    "model": "deepseek-chat",
    "userMessage": "用户问题",
    "assistantMessage": "模型回答",
    "startedAt": "2026-08-17T04:00:00Z",
    "completedAt": "2026-08-17T04:00:01Z",
    "inputTokens": 20,
    "outputTokens": 30,
    "latencyMs": 1000,
    "status": "SUCCESS"
  }'
```

同一工号下 `(sessionId, turnId)` 唯一，重复上报返回原记录。接口不接收思考过程、文件正文和 Tool 完整参数。

## Gateway 能力同步

```bash
curl -X POST http://127.0.0.1:8090/api/internal/gateway/capabilities/sync \
  -H "Authorization: Bearer <gateway-service-key>" \
  -H "Content-Type: application/json" \
  -d '{
    "capabilities": [
      {
        "type": "MCP",
        "externalRef": "workbuddy",
        "name": "WorkBuddy",
        "description": "企业 WMS 能力",
        "releaseVersion": "1",
        "sourceRef": "gateway:mcp:workbuddy"
      },
      {
        "type": "TOOL",
        "parentExternalRef": "workbuddy",
        "externalRef": "workbuddy/inventory-query",
        "name": "库存查询",
        "description": "查询当前用户可见库存",
        "releaseVersion": "1",
        "sourceRef": "gateway:tool:inventory-query"
      }
    ]
  }'
```

首次同步只产生 `DISCOVERED` 记录，不能直接暴露。Tool 的准入、发布和停用随所属 MCP 服务级联，不提供独立生命周期操作；已经发布的 MCP 后续同步发现新 Tool 时，新 Tool 自动进入 `PUBLISHED`，但仍受该 MCP 的用户授权和 Tool 排除策略约束。

管理端能力列表可通过 `status` 包含一个状态，或通过 `excludeStatus` 排除一个状态，二者不能同时使用。工作台的“已登记能力”使用
`GET /api/admin/capabilities?excludeStatus=DISABLED`，因此停用记录不会进入指标。

## 用户授权管理

管理端按当前有效授权用户分页读取：

```http
GET /api/admin/grants/subjects?keyword=5000&page=0&size=30
```

返回工号、有效直接授权数量、能力摘要和最近到期时间。授权明细仍通过
`GET /api/admin/grants?workcode={workcode}` 读取；新增或修改同一能力授权统一调用：

```http
POST /api/admin/grants
Content-Type: application/json

{
  "workcode": "1000001",
  "capabilityId": "<capability-uuid>",
  "validFrom": null,
  "validUntil": null
}
```

`DELETE /api/admin/grants/{grantId}` 撤销单项授权；
`DELETE /api/admin/grants/subjects/{workcode}` 逻辑撤销该工号全部 enabled 直接授权并记录管理审计，不物理删除历史记录。

Tool 不允许直接授权。授权 MCP 即默认开放其全部已发布 Tool；需要对某一用户收窄能力时，从 MCP 详情读取并维护例外：

```http
GET    /api/admin/capabilities/{mcpId}/tools
GET    /api/admin/grants/subjects/{workcode}/mcps/{mcpId}/tools
POST   /api/admin/grants/subjects/{workcode}/mcps/{mcpId}/tools/{toolId}/exclusion
DELETE /api/admin/grants/subjects/{workcode}/mcps/{mcpId}/tools/{toolId}/exclusion
```

`POST .../exclusion` 取消该用户的单个 Tool，`DELETE .../exclusion` 恢复随 MCP 授权；例外记录不会改变 MCP 或 Tool 的全局发布状态。

## Skill、客户端插件与能力包

Skill 只允许管理员通过 `multipart/form-data` 上传，不再从 GitLab 获取：

```text
POST /api/admin/skills
file=<SKILL.md 或 ZIP>
displayName=<界面名称>
releaseVersion=<版本>
```

ZIP 必须只包含一个顶层或单一根目录下的 `SKILL.md`，并通过路径穿越、文件数量、压缩前后大小和 YAML 元数据校验。上传成功后能力状态为 `DISCOVERED`。

客户端插件只允许管理员上传 ZIP：

```text
POST /api/admin/client-plugins
file=<插件 ZIP>
```

ZIP 顶层必须含 `plugin.json`，其中 `schemaVersion=1`，`id` 为 kebab-case，`entry` 指向包内存在的 `.mjs` 文件，`activation` 必须为 `hot`。上传后仍需通过通用能力接口依次准入和发布。已授权用户通过 `GET /api/client/client-plugins/{id}/artifact` 下载；Hub 和 Harness 两侧都会校验制品边界与完整性。插件授权、撤销和版本替换由 Harness 的受管 Patch 事务热激活；清单缺少热生命周期声明时上传直接拒绝。同一 `externalRef` 的新发布版本必须满足更高 SemVer，现有用户授权与 Bundle 引用会原子迁移，不需要重新审批。

管理员通过 `POST /api/admin/bundles` 创建能力包并提交 `memberIds`。Bundle 只组合 MCP、Skill 和客户端插件，不直接选择 Tool；Tool 随成员 MCP 展开。只有所有成员都已发布时，能力包才允许发布。客户端 `GET /api/client/catalog` 返回当前工号展开后的 `mcps`、`skills`、`bundles`、`plugins` 和当前全员企业指令，Harness 只安装该目录中的 Skill 与客户端插件。

## 企业市场申请

Harness 读取全部已发布能力与当前用户状态：

```http
GET /api/client/capability-market
```

该接口要求当前用户拥有 `PLUGIN_MARKET` 菜单权限；未授权返回 `403 MENU_NOT_GRANTED`。能力市场不展示 Tool 和企业指令，Tool 从 MCP 服务详情查看，企业指令由管理员全员发布。

用户提交申请：

```http
POST /api/client/capability-market/applications
Content-Type: application/json

{"capabilityId":"<capability-uuid>","reason":"业务用途"}
```

管理员通过 `GET /api/admin/capability-applications` 查看申请，再调用 `POST /api/admin/capability-applications/{id}/approve` 或 `/reject`。批准会生成普通用户授权；管理员也可通过用户授权 CRUD 直接授权，实现主动推送。

## Gateway 运行时授权

`tools/list` 和 `tools/call` 均调用：

```bash
curl -X POST http://127.0.0.1:8090/api/internal/authorization/check \
  -H "Authorization: Bearer <gateway-service-key>" \
  -H "Content-Type: application/json" \
  -d '{
    "workcode": "12345",
    "type": "TOOL",
    "externalRef": "workbuddy/inventory-query",
    "action": "CALL"
  }'
```

Tool 只有在自身及所属 MCP 均已发布时才可能返回 `allowed=true`。用户必须通过直接授权或 Bundle 获得所属 MCP，默认开放该服务下全部已发布 Tool；命中用户级 `subject_tool_exclusion` 时返回 `TOOL_EXCLUDED`。批量装配可读取 `GET /api/internal/authorization/snapshot/{workcode}`，但 Gateway 必须设置短 TTL 并在策略失效时默认拒绝。

## 企业指令

管理员通过 `POST /api/admin/instructions` 上传 UTF-8 `AGENTS.md`，并提供稳定标识、展示名称和语义版本。通过通用准入接口批准后发布；同一稳定标识只能发布高于当前版本的新版本，发布会在同一事务内替换旧版本，已发布版本不允许停用。企业指令不接受用户申请或用户授权。

客户端 `GET /api/client/catalog` 始终返回当前已发布的全员企业指令；`GET /api/client/instructions/{id}/artifact` 只允许下载该稳定标识当前发布的版本。

## 客户端升级

管理员上传草稿、发布和下载制品：

```http
GET  /api/admin/desktop-releases
POST /api/admin/desktop-releases
POST /api/admin/desktop-releases/{id}/publish
GET  /api/admin/desktop-releases/{id}/artifact
```

上传使用 `multipart/form-data`，字段为 `platform`、`version`、`releaseNotes` 和 `file`。`platform` 支持 `MAC_ARM64`、`MAC_X64`、`WINDOWS_X64`；macOS 只接受 `.dmg`/`.pkg`，Windows 只接受 `.exe`。上传后是 `DRAFT`，发布必须高于该平台当前 SemVer。

客户端使用已验签的 WorkBuddy Bearer JWT 查询和下载：

```http
GET /api/client/desktop-releases/latest?platform=MAC_ARM64&currentVersion=0.1.0-rc.17
GET /api/client/desktop-releases/{id}/artifact
```

没有更高版本时首个接口返回 `204`。制品响应包含准确 `Content-Length` 和以 SHA-256 形成的 `ETag`；客户端仍必须对完整下载内容重新计算 SHA-256，不能只信任响应头。

Windows x64 客户端改用 `electron-updater` 的鉴权 Generic Provider：

```http
GET /api/client/desktop-updates/windows/latest.yml
GET /api/client/desktop-updates/windows/artifacts/{id}.exe
```

`latest.yml` 只描述当前已发布的 `WINDOWS_X64` 版本，并包含相对制品地址、准确大小、发布时间和 SHA-512。两个接口都要求同一 WorkBuddy Bearer JWT；旧数据没有 SHA-512 时，服务端从受控制品重新计算，以兼容升级前已上传的 Windows 版本。客户端以 SHA-512 完成安装包校验后才允许静默覆盖和重启。当前不发布 blockmap，因此客户端禁用差分下载。

## 菜单权限

菜单访问权与能力授权分离。管理员以 CRUD 列表维护用户或部门授权：

```http
GET    /api/admin/menu-permissions?menuKey=PLUGIN_MARKET&subjectType=USER&keyword=5000
POST   /api/admin/menu-permissions
PUT    /api/admin/menu-permissions/{id}
DELETE /api/admin/menu-permissions/{id}
```

请求体示例：

```json
{
  "menuKey": "PLUGIN_MARKET",
  "subjectType": "USER",
  "subjectCode": "1000001"
}
```

`subjectType` 支持 `USER` 和 `DEPARTMENT`。客户端调用 `GET /api/client/menu-entitlements` 获取当前可见菜单；部门匹配来自已验签 JWT 的 `department_codes` claim，字符串或字符串数组均可。当前首个菜单键为 `PLUGIN_MARKET`。

## API Key 申请与领取

申请：

```bash
curl -X POST http://127.0.0.1:8090/api/client/key-applications \
  -H "Authorization: Bearer <user-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"purpose":"Harness Enterprise 模型访问"}'
```

查询当前用户申请：

```bash
curl http://127.0.0.1:8090/api/client/key-applications \
  -H "Authorization: Bearer <user-jwt>"
```

管理员在审批界面录入 Provider 标识、OpenAI 兼容模型网关地址和 Key 并批准后，一次性领取：

```bash
curl -X POST http://127.0.0.1:8090/api/client/key-applications/<application-id>/secret:claim \
  -H "Authorization: Bearer <user-jwt>"
```

领取响应包含 `apiKey`、`provider` 和 `baseUrl`。领取成功后同一接口再次调用返回 `410 Gone`；
列表接口不返回明文 Key，但会持续返回掩码、状态、Provider 和网关地址，便于客户端把本机加密
保存的 Key 与 Hub 当前接入地址组合使用。
