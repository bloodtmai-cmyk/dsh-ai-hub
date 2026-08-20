# 企业视觉工具箱

这是从 [Anionex/dsh-vision-toolkit](https://github.com/Anionex/dsh-vision-toolkit) 图片分析工作流中裁剪出的企业受管实现。上游 MIT 署名见 [UPSTREAM_LICENSE.md](UPSTREAM_LICENSE.md)。本实现保留元数据检查、裁剪、像素差异、主色和视觉理解，不引入外部共享服务、用户 API Key、自更新、TLS 绕过或运行期 Python 下载。

插件从 Harness 托管环境读取固定的 `MODEL_GATEWAY_BASE_URL`、`MODEL_GATEWAY_API_KEY` 和 `MODEL_GATEWAY_DEFAULT_MODEL`。旧版 `LITELLM_*` 名称只作为迁移兼容。所有文件路径在真实路径解析后限制于当前工作区；派生文件只能写回已存在的工作区目录，并拒绝覆盖已有文件或符号链接。
