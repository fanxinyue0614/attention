---
status: accepted
date: 2026-11-07
---

# 助手审计轨迹与 AI 快照的存储归属

## 决策

issue #231 引入的助手诊断数据按以下方式落盘,不属于 ADR-0002 枚举的任何一类,特此登记:

- 助手运行摘要(agent trace,滚动保留最近 50 条)使用**独立的 Preferences DataStore 文件**(`agent_trace`)。它是「诊断数据」:随手可丢、只增不改、无实体关系,整份轨迹作为一条版本化 JSON envelope 存进单个 Preferences key,一次 edit 即一次原子提交。
- AI 变更前快照复用备份编码协议(`AttentionBackupCodec`),以固定 3 个环形文件槽位写入 `filesDir`,与用户手动备份(`attention_import_backup.json`)命名空间互不覆盖;它不是运行时主存储,只在需要回滚时按备份导入流程恢复。
- 快照内容不含任何密钥:API key 只存在于 `BuildConfig`/`local.properties`,从不进入 `AttentionState`,结构上不可能被序列化进快照。

## 背景

ADR-0002 把 Preferences DataStore 的职责枚举为设置类(规划日界线、周起始日、每日容量、首屏、上次打开页面、引导状态、通知偏好),业务事实归 Room。审计轨迹既非设置亦非业务实体,按原决策无处安放;若硬塞进 Room 则为一项随手可丢的诊断数据引入表结构与迁移成本,不成比例。

## 结果

- `com.attention.app.data.audit` 包提供 `AgentTraceStore`(DataStore)、`AiSnapshotStore`(环形文件)与门面 `AiChangeAudit`。
- 写入点在组合根的 CommandExecutor 包装层,待 #227 合入后接线;接线前这些能力对用户不可见。
- 轨迹坏数据按「整段读作空、坏条目跳过」降级,不崩助手与设置页。

## 代价与风险

- DataStore 文件家族多一个成员;今后新增持久化数据类别时,须在 ADR-0002 或本 ADR 登记归属,避免边界再次失守。
- 诊断数据不参与备份/导入协议,卸载即失,这是有意取舍。
- 快照数据与槽位索引分两次原子写,断电窗口内最多重复占用一个槽位,不会写出第 4 份文件。
