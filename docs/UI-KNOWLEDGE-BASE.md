# APP UI 知识库

知识库把 UI 自动化从“93 种步骤平铺”转为 **APP → 页面 → 控件 → 操作** 的可维护资产。

## 模型

| 层级 | 字段 |
|---|---|
| APP | name、packageName、platform、description、iconUrl |
| 页面 | name、route/Activity、description、screenshotUrl |
| 控件 | name、**text**（显示文本）、**resourceId**（id）、**desc**（不显示，用于定位）、**intent**（功能意图）、controlType、screenshotUrl、bounds、source(MANUAL/AI/CRAWLER)、confidence |
| 操作 | actionType（CLICK/LONG_PRESS/SWIPE/INPUT/ASSERT_TEXT/SCROLL/CUSTOM）、label、params(JSON)、source、enabled |

一个控件可以关联多个基本操作，例如按钮关联 CLICK + LONG_PRESS，列表关联 SWIPE/SCROLL，输入框关联 INPUT。

## 页面

菜单 **UI 知识库**（`/#/kb`）：

- 左侧应用列表；右侧页面 chip 和控件卡片
- 新建/编辑/删除应用、页面、控件
- 控件卡片展示缩略图、text/id/intent/类型/来源/操作数
- 「操作」维护该控件的操作关联
- 页面上传截图；控件上传截图（存产物存储，返回 `/api/v1/kb/screenshots/...`）

## API

```text
GET/POST        /api/v1/kb/apps
GET/PUT/DELETE  /api/v1/kb/apps/{id}
GET/POST        /api/v1/kb/apps/{appId}/pages
GET/PUT/DELETE  /api/v1/kb/pages/{id}
GET/POST        /api/v1/kb/pages/{pageId}/controls
POST            /api/v1/kb/pages/{pageId}/controls/batch   # 爬虫/OCR 批量入库
GET/PUT/DELETE  /api/v1/kb/controls/{id}
GET/POST        /api/v1/kb/controls/{controlId}/actions
PUT/DELETE      /api/v1/kb/actions/{id}
POST            /api/v1/kb/screenshots                      # multipart file
GET             /api/v1/kb/screenshots/{name}              # 公开缩略图
```

批量导入示例（未来爬虫/OCR/图像提取脚本直接调用）：

```json
{
  "controls": [
    {"name":"登录按钮","text":"登录","resourceId":"btn_login","desc":"login submit",
     "intent":"提交登录","controlType":"BUTTON","source":"CRAWLER","screenshotUrl":"/api/v1/kb/screenshots/xxx.png"},
    {"name":"用户名","resourceId":"et_user","controlType":"INPUT","source":"CRAWLER"}
  ]
}
```

## 与执行链路的衔接

当前知识库是可编辑资产层，已提供 source/confidence 字段区分人工、AI、爬虫来源。后续可：

1. 将知识库操作映射为 `case_step`（CLICK/INPUT/SWIPE/ASSERT_TEXT），即“知识库驱动步骤生成”。
2. 爬虫/OCR：dump 控件树 + 页面截图 → 图像裁切 → 批量 `controls/batch` → 人工 review/补 intent → AI 建议操作。
3. 元素库（`ui_element`）可继续作为跨 APP 的轻量定位器，知识库则保留页面上下文与截图。

## 与 Sonic 的差异

Sonic 是 `test_cases + steps + elements` 平铺模型；TestHub 保留步骤用例，同时新增产品级知识库，适合“先建模 APP，再生成/复用步骤”的路线。
