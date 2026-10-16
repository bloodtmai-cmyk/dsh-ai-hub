<h1 align="center">DSH AI Hub</h1>

<p align="center">
  <strong>面向企业内部 Harness 工作台的轻量控制面。</strong><br>
  集中维护身份接入、模型访问、能力授权、受管指令和审计记录。
</p>

<p align="center"><sub>独立社区项目，基于 DeepSeek Harness 的企业托管需求构建，与 DeepSeek AI 不存在隶属、合作、授权或背书关系。</sub></p>

[English](README.en.md) | 中文

## 项目定位

DSH AI Hub 是 [Harness Enterprise](https://github.com/bloodtmai-cmyk/dsh-harness-enterprise) 的配套控制面。两者共同提供受管的企业内部智能工作台入口：Hub 维护配置和授权，Harness Enterprise 提供面向用户的桌面运行时。

Hub 不代理正常的模型请求或 MCP Tool 调用，也不是身份提供方，不替代 LDAP、SSO、业务系统、数据源及其权限控制。

当前代码覆盖以下范围：

- 管理员登录、Session/CSRF 防护和管理操作审计。
- 对话审计接入与按身份、时间、状态查询。
- MCP/Tool、Skill、Bundle 和客户端插件的准入、发布、申请与授权。
- 只读、版本化、全员生效的企业 `AGENTS.md` 指令。
- 桌面版本元数据与受管制品分发接口。
- 模型访问申请、管理员录入和一次性领取。

项目保留清晰边界：身份由可替换的 Enterprise Gateway 验证；Gateway 在 `tools/list` 和 `tools/call` 阶段执行 Hub 当前授权；下游业务系统继续负责最终的数据权限。

## 配套项目

[Harness Enterprise](https://github.com/bloodtmai-cmyk/dsh-harness-enterprise) 消费 Hub 提供的策略、受管制品、模型端点元数据和客户端版本信息。两个仓库保持独立，使组织可以替换任一侧，而不需要让 Hub 成为业务流量代理。

## 目录

```text
backend/         Spring Boot 3、Java 21、Spring Security、JPA、Flyway
frontend/        React、TypeScript、Vite 管理界面
extension-host/  受控 Cordis Provider/Connector 宿主
artifacts/       可公开审查的示例插件与企业指令
docs/            架构、API 和安全边界
deploy/          通用构建与部署示例
```

## 模型访问

企业托管桌面不允许用户手工填写模型凭据。管理员批准时，Hub 将以下内容保存为同一份授权：

- Provider 标识；
- OpenAI 兼容模型网关地址；
- API Key。

Key 加密保存并只允许领取一次；Provider 和网关地址可在授权有效期间持续读取。旧授权缺少地址元数据时默认拒绝，必须重新签发，不会猜测默认地址。模型、预算和有效期由上游 Provider 管理。LiteLLM 可以作为网关实现，但不是系统依赖。

标准 Harness 仍保留第三方模型配置，该限制只适用于企业托管桌面。

## 本地运行

需要 Java 21、Maven、Node.js 22+ 和 npm。

```bash
cd extension-host
npm ci
npm test
npm run build

cd ../frontend
npm ci
npm run build

cd ../backend
mvn spring-boot:run
```

默认后端地址为 `http://127.0.0.1:8090`，本地 profile 使用 H2。管理前端开发服务器可单独运行：

```bash
cd frontend
npm run dev
```

PostgreSQL、生产环境变量和反向代理示例见 [.env.example](.env.example)、[部署脚本](deploy/)与[架构说明](docs/architecture.md)。生产接入必须使用受信 HTTPS 地址，并通过 Secret 管理系统注入密钥。

## 验证

```bash
cd backend && mvn test
cd ../frontend && npm run build
cd ../extension-host && npm test && npm run build
cd .. && node --test artifacts/*/tests/*.test.mjs
./scripts/verify-community-sanitization.sh
```

本仓库只发布源码，不提供 Harness Enterprise 的 macOS 或 Windows 安装包。客户端构建、签名和分发由具体部署者负责。

## 文档与维护

- [架构与信任边界](docs/architecture.md)
- [API 接入](docs/api.md)
- [参与贡献](CONTRIBUTING.md)
- [安全策略](SECURITY.md)

项目由 `clanie` 维护。安全问题请不要提交公开 Issue，应按 [SECURITY.md](SECURITY.md) 发送私密报告。

## License

项目采用 [MIT License](LICENSE)。第三方依赖和示例制品仍遵循各自的许可证与服务条款，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
