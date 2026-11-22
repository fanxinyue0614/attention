---
status: accepted
date: 2026-11-01
---

# ReAct 循环骨架：自建轻量引擎与只读工具边界

## 背景

Attention 是本地优先应用，README 承诺核心功能无需账号与网络；v0.1.0 把「AI 教练、自动排程」列为非目标。`feat/react` 的任务是为未来的应用内 AI 助手打底：先把 Reason+Act（推理-行动）循环立成可测的纯 JVM 骨架，再谈模型绑定。

生态调研（截至 2026-11-01）：Kotlin 生态没有可复用的「纯循环」微库；Koog 1.0 的 `reActStrategy` 与其 LLMClient/PromptExecutor 抽象及 Ktor 3.x 绑定；LangChain4j 依赖 Java 17 API，维护者确认在 Android API 34 以下崩溃（本项目 minSdk 26）。

## 决策

### 1. 自建纯 JVM 循环，不引框架

- 新模块 `:agent`（JVM 17，仅依赖 `:domain` 与 kotlinx-serialization），循环引擎约数百行，JUnit 全覆盖。
- 深模块边界：对外只有 `ReActAgent.run`；`Reasoner`（挂起接口，产出 Act/Answer 决策）与 `AgentTool`（同步纯投影）是两个单方法接缝；事件轨迹（`AgentEvent`）与终态（`AgentOutcome`）都是可序列化的密封值。

### 2. 错误全部设计成结果值

- 循环永不抛异常（协程取消除外）：推理器失败收敛为 `Failed`；工具异常、未知工具、参数不合法一律转成错误观测回填，推理器自行纠正；步数预算（`maxSteps`，默认 8）耗尽收敛为 `BudgetExhausted`。

### 3. 只读工具先行，变更类另立

- 首批工具 `query_targets` / `query_agenda` / `query_period_stats` / `query_timer` 均为 `AttentionState` 的纯投影，领域规则仍在 `:domain` 的 read-model 函数里，`:agent` 不新增领域知识。
- 变更类动作（提交领域 command）不从这个工具接口扩展：助手代用户改数据需要确认机制与危险操作基线（#186），属于后续阶段。

### 4. 本阶段零网络、零模型

- 不引入网络栈、不接 API key。挂起的 `Reasoner` 签名与工具的 JSON Schema 参数描述是下一阶段 LLM 绑定（云端 OpenAI 兼容端点或端侧 LiteRT-LM）的预留位；届时才需要修订隐私承诺与 README。

## 不采用的方案

- 不用 Koog：现成的 ReAct 策略会把 Ktor 网络栈与模型绑定拉进本阶段。
- 不用 LangChain4j：minSdk 26 下不可用。
- 不把工具做成「返回全量 state JSON」：内部字段泄漏进接口且载荷不可控。
- 不在本阶段做 UI：助手形态取决于模型能力，现在做会返工。
- 不让工具直接修改状态：确认机制不属于这个骨架。
- 领域「目标」一词已被目标树占用，循环输入一律称 `request`（请求）。

## 影响

- `settings.gradle.kts` 新增 `:agent`；CI 的 `test` 任务自动覆盖新模块。
- 「助手」词条进入 CONTEXT.md 领域语言；请求、推理-行动循环、工具等子词汇留在 GLOSSARY。
- 下一阶段（模型绑定）需要：网络栈选型、密钥管理、隐私声明与 README 修订、变更类工具与确认机制、trace 的持久化决策。

## 参考

- [Koog 1.0 发布（官方博客）](https://blog.jetbrains.com/ai/2026/05/koog-1-0-is-out-stable-core-better-interop-and-multiplatform-observability)
- [Koog 预定义 agent 策略（官方文档）](https://docs.koog.ai/predefined-agent-strategies/)
- [LangChain4j Android 兼容性（维护者表态，issue #2351）](https://github.com/langchain4j/langchain4j/issues/2351)
- ADR-0005：组合根与页面 seam 的职责边界（now/zone 注入约定）
- Issue #222：[Spec] ReAct 循环骨架
