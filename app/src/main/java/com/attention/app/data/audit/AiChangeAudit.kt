package com.attention.app.data.audit

import android.content.Context
import com.attention.domain.AttentionState
import java.time.Instant

/**
 * 审计轨迹与 AI 快照的唯一写入入口，供组合根的变更包装层调用。
 *
 * 两个方法对应两种不同的粒度：
 * - [beforeChange] 在**每一条**变更 command 提交前调用，打一份快照（滚动 3 份）；
 * - [recordRun] 在**一次**助手运行结束时调用，追加一条摘要（滚动 50 条）。
 *
 * 存储异常会照常向上抛：快照写不下去时宁可让这次变更为失败，也不要改动一份没有
 * 存档的数据。错误怎么呈现给用户由调用方决定，这里不做吞异常。
 */
class AiChangeAudit(
    private val traceStore: AgentTraceStore,
    private val snapshotStore: AiSnapshotStore,
    private val now: () -> Instant = Instant::now,
) {
    /** 提交变更前的兜底存档。 */
    suspend fun beforeChange(state: AttentionState) {
        snapshotStore.capture(state)
    }

    /**
     * 记录一次运行的结果。[commands] 只放 command 的名字，重复的按首次出现的顺序去重，
     * 因此「同一次运行里对同一目标改了两下」在审计里仍然只有一条名字。
     */
    suspend fun recordRun(
        request: String,
        outcome: AgentRunOutcome,
        commands: List<String> = emptyList(),
    ) {
        traceStore.record(
            AgentTraceEntry(
                request = request,
                outcome = outcome,
                commands = commands.distinct(),
                atEpochMillis = now().toEpochMilli(),
            ),
        )
    }
}

/**
 * 组合根用来装配默认实现：轨迹走独立的 Preferences DataStore，快照走 filesDir 下的
 * 环形文件槽位。等 `#227` 的 CommandExecutor 合并后，包装层只需要持有这个对象并调用
 * [AiChangeAudit.beforeChange] 与 [AiChangeAudit.recordRun]。
 */
fun Context.createAiChangeAudit(): AiChangeAudit = AiChangeAudit(
    traceStore = createAgentTraceStore(),
    snapshotStore = FileAiSnapshotStore(this),
)
