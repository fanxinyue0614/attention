package com.attention.app.data.audit

import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.domain.AttentionState
import com.attention.domain.Target
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAiSnapshotStoreTest {
    private fun temporaryDirectory(): File = Files.createTempDirectory("attention-ai-snapshot").toFile()

    private fun state(index: Int) = AttentionState(
        targets = listOf(Target(id = "target-$index", title = "state-$index")),
    )

    private fun titleOf(snapshot: AiSnapshot): String =
        AttentionBackupCodec.decode(snapshot.encoded).targets.single().title

    @Test
    fun the_fourth_capture_leaves_three_snapshots_and_overwrites_the_oldest() = runBlocking {
        val store = FileAiSnapshotStore(temporaryDirectory())

        (0 until 4).forEach { index -> store.capture(state(index)) }

        // 槽位 1 被第 4 次写入覆盖，因此目录里只剩 3 份。
        assertEquals(3, store.snapshots().size)
        assertEquals(
            setOf("state-1", "state-2", "state-3"),
            store.snapshots().map(::titleOf).toSet(),
        )
        assertEquals("state-3", titleOf(requireNotNull(store.latest())))
    }

    @Test
    fun snapshots_stay_at_three_files_no_matter_how_many_changes_happen() = runBlocking {
        val directory = temporaryDirectory()
        val store = FileAiSnapshotStore(directory)

        repeat(10) { index -> store.capture(state(index)) }

        val snapshotFiles = directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(FileAiSnapshotStore.FILE_PREFIX) && it.extension == "json" }
        assertEquals(3, snapshotFiles.size)
        assertEquals(3, store.snapshots().size)
    }

    @Test
    fun ai_snapshots_never_touch_the_manual_import_backup() = runBlocking {
        val directory = temporaryDirectory()
        val manualBackup = File(directory, "attention_import_backup.json")
        manualBackup.writeText("manual-backup-content")
        val store = FileAiSnapshotStore(directory)

        repeat(5) { index -> store.capture(state(index)) }

        assertEquals("manual-backup-content", manualBackup.readText())
        assertTrue(store.snapshots().none { it.encoded == "manual-backup-content" })
    }

    @Test
    fun snapshot_payload_carries_no_api_key() = runBlocking {
        val store = FileAiSnapshotStore(temporaryDirectory())
        store.capture(state(0))

        val encoded = requireNotNull(store.latest()).encoded.lowercase()
        listOf("apikey", "api_key", "authorization", "sk-").forEach { secret ->
            assertFalse("快照里出现了疑似密钥字段：$secret", encoded.contains(secret))
        }
    }

    @Test
    fun a_non_positive_keep_count_is_rejected() {
        val rejected = runCatching { FileAiSnapshotStore(temporaryDirectory(), keep = 0) }

        assertTrue(rejected.isFailure)
    }
}
