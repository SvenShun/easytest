# MeterSphere 接口自动化对照

## MeterSphere 的能力（本地源码）

MeterSphere 的 API 测试不是“单接口 + 断言”：

- **API 定义/API 用例**：请求、环境、断言、前后置处理器。
- **API 场景**：`ApiScenario` + `ApiScenarioStep`/`StepBlob`，步骤可引用接口/用例/子场景/自定义请求。
- **控制器**：循环、条件（if）、一次控制器、等待。
- **脚本**：BeanShell/Groovy/JSR223 前置/后置/断言。
- **SQL**：`SQLProcessor` 作为前置/后置处理器。
- **变量/数据集**：场景变量、CSV、环境变量、提取变量。
- **执行**：JMeter 引擎（非 Spring 自研），支持多线程/报告/通知。
- **报告**：`ApiScenarioReport`、步骤明细、失败日志。
- **调度**：场景/计划定时任务、批量执行。
- **导入导出**：JMeter/JMeter XML、场景导入。

## TestHub 已落地

| 能力 | 状态 |
|---|---|
| 单接口用例 + HTTP + 5 类断言 + 环境 | 已有 |
| 多步骤 YAML 场景 | ✅ 本轮 |
| if/else、loop/times/items/dataset | ✅ |
| 数据集行迭代 | ✅ YAML datasets（CSV 文件导入待补） |
| HTTP/SQL/Groovy/set/assert/wait 步骤 | ✅ |
| 步骤间变量提取/传递 | ✅ JSONPath/header/status/SQL rows |
| 场景 CRUD、校验、运行历史、步骤报告 | ✅ |
| 场景挂载到测试计划、计划级报告 | ✅ |
| 场景队列/worker/启动恢复 | ✅ |
| 定时任务跑计划（含场景计划） | ✅ |
| 场景直接定时 | ✅ Jobs 选择 API 场景 |
| 前端 YAML 编辑/运行/历史 | ✅ |
| 通知机器人 | ✅（计划终态） |

## 尚未对齐（按优先级）

1. ~~控制器~~：已支持 if/else、loop（times/items/dataset）、once、while。
2. ~~数据集~~：已支持 YAML `datasets` + loop 行迭代 + CSV 导入编辑器。
3. ~~场景直接定时~~：已支持 Jobs 任务直接选择 API 场景目标（无需先建计划）。
4. **场景批量运行/选择多个场景**：当前逐个运行。
5. **前置/后置处理器抽象**：当前脚本/SQL是普通步骤，不能绑定到每个 HTTP 步骤。
6. **变量作用域/优先级**：环境、场景、步骤、提取变量的覆盖规则可更细。
7. **导入导出**：JMeter XML/JSON/YAML 互转。
8. **断言脚本/JSON Schema/XML**：当前 HTTP 5 类 + SQL/变量。
9. **场景报告通知**：场景直接运行不触发通知机器人（计划运行会）。
10. **JMeter 生态组件**：不计划引入 JMeter。

## 设计取向

不嵌入 JMeter：TestHub 已有自己的队列、worker、HTTP 客户端、断言与计划模型。YAML 场景驱动器更易版本化、生成、评审，后续可把知识库/爬虫生成的步骤直接落成 YAML 或 `case_step`。
