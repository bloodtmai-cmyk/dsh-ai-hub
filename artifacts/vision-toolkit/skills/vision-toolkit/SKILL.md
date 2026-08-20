---
name: vision-toolkit
description: >
  Use for inspecting, cropping, comparing, reading, or understanding image files
  inside the current workspace through the AI Hub-managed vision tools.
---

# 企业视觉工具箱

只处理当前工作区内的图片，并按任务选择最小能力。

## 路由

1. 图片尺寸、格式、透明通道和方向：`vision_inspect`。
2. 生成局部裁剪图：先检查尺寸，再调用 `vision_crop`。
3. 两张等尺寸图片的像素差异：`vision_compare`。
4. 提取近似主色：`vision_palette`。
5. 图片描述、OCR、对象检测或定位：`vision_understand`。

## 约束

- 优先使用确定性的本地工具，只有语义理解任务才调用 Hub 托管模型网关。
- 不安装 Python、OCR 或模型依赖，不调用用户指定的外部视觉服务。
- 不要求用户填写 API Key、服务地址或模型名；这些由企业桌面环境托管。
- 不读取工作区外路径。裁剪结果只能写入工作区内已存在的目录。
- `vision_understand` 提示模型不支持图片时，明确告知需要管理员在模型网关配置视觉模型，不要绕过企业配置。
