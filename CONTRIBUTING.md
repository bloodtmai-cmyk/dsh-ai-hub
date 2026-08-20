# 参与贡献

感谢参与 DSH AI Hub。提交前请先确认改动属于通用能力治理，而不是某个组织的业务实现。

## 开发检查

```bash
cd backend && mvn test
cd ../frontend && npm run build
cd ../extension-host && npm test && npm run build
cd .. && ./scripts/verify-community-sanitization.sh
```

提交不得包含真实组织名称、Logo、域名、IP、账号、访问凭据、生产数据、内部截图或专有业务接口。示例使用 `example.com`、回环地址和显式占位值。

新增 Connector、客户端插件或 Skill 时，请同时说明来源、许可证、权限、网络访问、身份边界、升级方式和失败策略。Tool 不应绕过 Gateway 提供的可信身份。

## 变更范围

- Core 负责身份验签、目录、授权、审计、模型访问配置与制品治理。
- Gateway 负责 MCP 协议和执行，AI Hub 不代理正常 Tool 流量。
- 组织专有 HR、WMS、OA 等实现应保留在私有扩展仓库，不进入社区 Core。
