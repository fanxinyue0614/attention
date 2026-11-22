package com.attention.app.data.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTraceRollingTest {
    private fun entry(index: Int) = AgentTraceEntry(
        request = "request-$index",
        outcome = AgentRunOutcome.ANSWERED,
        commands = listOf("RecordTime"),
        atEpochMillis = index.toLong(),
    )

    @Test
    fun appending_more_than_fifty_entries_drops_the_oldest_and_keeps_order() {
        var entries = emptyList<AgentTraceEntry>()
        repeat(55) { index -> entries = AgentTraceRolling.append(entries, entry(index)) }

        assertEquals(AgentTraceRolling.MAX_ENTRIES, entries.size)
        // 55 条里保留最后 50 条：第 1..5 条被淘汰，头尾仍然按时间正序。
        assertEquals("request-5", entries.first().request)
        assertEquals("request-54", entries.last().request)
    }

    @Test
    fun exactly_fifty_entries_are_kept_untouched() {
        var entries = emptyList<AgentTraceEntry>()
        repeat(50) { index -> entries = AgentTraceRolling.append(entries, entry(index)) }

        assertEquals(50, entries.size)
        assertEquals("request-0", entries.first().request)
        assertEquals("request-49", entries.last().request)
    }

    @Test
    fun custom_capacity_is_honoured() {
        var entries = emptyList<AgentTraceEntry>()
        repeat(4) { index -> entries = AgentTraceRolling.append(entries, entry(index), maxEntries = 2) }

        assertEquals(listOf("request-2", "request-3"), entries.map { it.request })
    }

    @Test
    fun a_non_positive_capacity_is_rejected() {
        val rejected = runCatching { AgentTraceRolling.append(emptyList(), entry(0), maxEntries = 0) }

        assertTrue(rejected.isFailure)
    }
}
