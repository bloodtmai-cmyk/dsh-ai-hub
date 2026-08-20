# 企业微信邮件、待办与微盘

## 邮件

使用 `wecom_mail_call`：`search`、`get`、`send`。发送是写操作，必须在调用前确认收件人、主题和正文，不得用本地文件路径添加附件。

## 待办

使用 `wecom_todo_call`：`list`、`get`、`create`、`update`、`finish`。创建、更新和完成属于写操作。

## 微盘

使用 `wecom_drive_call`：`list_recent`、`search`、`get`。当前只开放只读检索和详情，不开放上传、下载、重命名或创建文件夹。
