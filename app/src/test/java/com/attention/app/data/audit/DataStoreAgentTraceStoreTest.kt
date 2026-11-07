package com.attention.app.data.audit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreAgentTraceStoreTest {
    private fun temporaryFile() = Files.createTempFile("attention-agent-trace", ".preferences_pb").toFile()

    private fun entry(index: Int) = AgentTraceEntry(
        request = "request-$index",
        outcome = AgentRunOutcome.ANSWERED,
        commands = listOf("RecordTime"),
        atEpochMillis = index.toLong(),
    )

    @Test
    fun recording_more_than_fifty_entries_keeps_the_latest_fifty_and_survives_restart() = runBlocking {
        val file = temporaryFile()
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreAgentTraceStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })

        repeat(55) { index -> store.record(entry(index)) }

        val entries = store.entries.first()
        assertEquals(50, entries.size)
        assertEquals("request-5", entries.first().request)
        assertEquals("request-54", entries.last().request)

        firstScope.cancel()
        firstScope.coroutineContext[Job]?.join()

        val reopened = DataStoreAgentTraceStore(PreferenceDataStoreFactory.create { file })
        val restored = reopened.entries.first()
        assertEquals(50, restored.size)
        assertEquals("request-54", restored.last().request)

        file.delete()
        Unit
    }

    @Test
    fun a_corrupted_value_reads_as_an_empty_trace_instead_of_failing() = runBlocking {
        val file = temporaryFile()
        val dataStore = PreferenceDataStoreFactory.create { file }
        // 直接写坏值，模拟旧版本格式或写到一半的文件。
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("agent_trace_entries")] = "{not a valid trace"
        }

        val store = DataStoreAgentTraceStore(dataStore)

        assertTrue(store.entries.first().isEmpty())
        file.delete()
        Unit
    }

    @Test
    fun a_broken_entry_inside_the_array_does_not_discard_the_healthy_ones() = runBlocking {
        val file = temporaryFile()
        val dataStore = PreferenceDataStoreFactory.create { file }
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("agent_trace_entries")] = """
                {"version":1,"entries":[
                  {"request":"健康的请求","outcome":"answered","commands":["SetGoal"],"atEpochMillis":42},
                  {"request":"缺少必填字段"}
                ]}
            """.trimIndent()
        }

        val store = DataStoreAgentTraceStore(dataStore)
        val entries = store.entries.first()

        assertEquals(1, entries.size)
        assertEquals("健康的请求", entries.single().request)
        assertEquals(42L, entries.single().atEpochMillis)

        file.delete()
        Unit
    }

    @Test
    fun clear_empties_the_trace_and_new_runs_start_from_zero() = runBlocking {
        val file = temporaryFile()
        val store = DataStoreAgentTraceStore(PreferenceDataStoreFactory.create { file })

        store.record(entry(1))
        store.record(entry(2))
        assertEquals(2, store.entries.first().size)

        store.clear()
        assertTrue(store.entries.first().isEmpty())

        store.record(entry(3))
        assertEquals(listOf("request-3"), store.entries.first().map { it.request })

        file.delete()
        Unit
    }
}
