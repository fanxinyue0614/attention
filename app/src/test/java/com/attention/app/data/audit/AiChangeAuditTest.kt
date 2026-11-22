package com.attention.app.data.audit

import com.attention.domain.AttentionState
import com.attention.domain.Target
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChangeAuditTest {
    private class RecordingTraceStore : AgentTraceStore {
        val recorded = mutableListOf<AgentTraceEntry>()

        override val entries: Flow<List<AgentTraceEntry>> = flowOf(emptyList())

        override suspend fun record(entry: AgentTraceEntry) {
            recorded += entry
        }

        override suspend fun clear() = Unit
    }

    private class RecordingSnapshotStore : AiSnapshotStore {
        val captured = mutableListOf<AttentionState>()

        override suspend fun capture(state: AttentionState) {
            captured += state
        }

        override suspend fun snapshots(): List<AiSnapshot> = emptyList()

        override suspend fun latest(): AiSnapshot? = null
    }

    private val state = AttentionState(targets = listOf(Target(id = "t", title = "雅思")))

    @Test
    fun before_change_captures_the_given_state_exactly_once() = runBlocking {
        val snapshots = RecordingSnapshotStore()
        val audit = AiChangeAudit(RecordingTraceStore(), snapshots)

        audit.beforeChange(state)

        assertEquals(listOf(state), snapshots.captured)
    }

    @Test
    fun record_run_writes_one_summary_with_the_injected_clock() = runBlocking {
        val traces = RecordingTraceStore()
        val audit = AiChangeAudit(
            traceStore = traces,
            snapshotStore = RecordingSnapshotStore(),
            now = { Instant.ofEpochMilli(1_700_000_000_000) },
        )

        audit.recordRun("帮我记 25 分钟阅读", AgentRunOutcome.ANSWERED, listOf("RecordTime", "SetGoal"))

        val entry = traces.recorded.single()
        assertEquals("帮我记 25 分钟阅读", entry.request)
        assertEquals(AgentRunOutcome.ANSWERED, entry.outcome)
        assertEquals(listOf("RecordTime", "SetGoal"), entry.commands)
        assertEquals(1_700_000_000_000, entry.atEpochMillis)
    }

    @Test
    fun repeated_command_names_are_recorded_once_in_first_seen_order() = runBlocking {
        val traces = RecordingTraceStore()
        val audit = AiChangeAudit(traces, RecordingSnapshotStore())

        audit.recordRun("再记一笔", AgentRunOutcome.ANSWERED, listOf("RecordTime", "SetGoal", "RecordTime"))

        assertEquals(listOf("RecordTime", "SetGoal"), traces.recorded.single().commands)
    }

    @Test
    fun a_failed_run_is_still_recorded_with_its_final_state() = runBlocking {
        val traces = RecordingTraceStore()
        val audit = AiChangeAudit(traces, RecordingSnapshotStore())

        audit.recordRun("帮我整理目标", AgentRunOutcome.FAILED)

        val entry = traces.recorded.single()
        assertEquals(AgentRunOutcome.FAILED, entry.outcome)
        assertTrue(entry.commands.isEmpty())
    }
}
