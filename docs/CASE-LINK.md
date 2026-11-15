# 文本用例与自动化用例关联

文本/BDD 用例可以和自动化资产建立轻量标记，不做断言级映射：

| 类型 | 含义 |
|---|---|
| `API_CASE` | 单接口/单步组件 |
| `API_SCENARIO` | YAML 多步骤 API 场景 |
| `UI_CASE` | UI/客户端自动化用例 |

接口：

- `GET /api/v1/cases/{caseId}/automation-links`
- `POST /api/v1/cases/{caseId}/automation-links` body `{"targetType":"API_CASE","targetId":"ac_..."}`
- `DELETE /api/v1/cases/{caseId}/automation-links/{linkId}`

返回的 `latestStatus` 来自最近一次计划运行项/场景运行记录；目标不存在时显示“已删除”。关联腐化由后续独立巡检处理，不阻塞用例编辑。
