# 企业微信文档与表格

只通过 `wecom_doc_call` 和 `wecom_sheet_call` 调用下列固定操作。

## Word 文档

- `search`: 按关键词检索文档。
- `get_content`: 读取 Word 内容，`request_json` 至少传 `docid`，可传 `content_type: "markdown"`。
- `create`: 创建在线文档。必填 `doc_name`，可传 `content` 和 `content_type: "text" | "markdown"`；不得使用 `title` 字段。
- `append_content`: 向 Word 末尾追加文本，传 `docid`、`content`。
- `overwrite_content`: 全量覆盖 Word 内容，传 `docid`、`content`、`content_type`。
- `rename`: 重命名文档。

## 表格

- `get`: 获取表格及工作表信息。
- `get_range`: 按 A1 范围读取单元格。
- `create`: 创建表格。
- `update_range`: 更新指定范围。
- `append_row`: 在工作表末尾追加一行。
- `add_subsheet`: 新增工作表。

创建、覆盖、更新和追加属于写操作，调用前必须向用户明确说明目标与影响。不得传本地文件路径。

创建一个带初始正文的 Word 文档示例：

```json
{
  "doc_name": "项目周报",
  "content": "本周进展",
  "content_type": "text",
  "doc_type": "doc"
}
```
