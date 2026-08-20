# 安全策略

请不要在公开 Issue 中披露可利用的漏洞、访问凭据、内部地址或生产数据。请发送到 [bloodtmai@gmail.com](mailto:bloodtmai@gmail.com)；仓库启用 GitHub Private Vulnerability Reporting 后，也可以通过该渠道提交。

报告应包含受影响版本、部署模式、复现条件、预期影响和最小化日志。请删除 Token、API Key、Cookie、员工标识和业务数据。

维护者会在七个自然日内确认收到报告。修复时间取决于严重程度、可复现性，以及问题是否属于本仓库或上游依赖。

以下边界属于安全不变量：

- 客户端身份只能来自已验签的 Gateway JWT。
- 管理 API 使用 Session 与 CSRF，内部 API 使用独立服务凭据。
- 模型 API Key 和审计正文必须加密保存；Key 只能领取一次。
- Provider、模型网关地址和 Key 必须作为同一份 Hub 授权下发。
- Gateway 在 `tools/list` 和 `tools/call` 阶段均应默认拒绝未授权能力。
- 上传制品必须校验路径、大小、格式、完整性和版本单调性。
