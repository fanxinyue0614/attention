package com.attention.app.data.audit

import android.content.Context
import com.attention.app.data.backup.AttentionBackupCodec
import com.attention.domain.AttentionState
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一份 AI 变更前的本地快照。[slot] 是环形槽位，只用于诊断，不承诺与时间顺序一致。 */
data class AiSnapshot(val slot: Int, val encoded: String)

/** 助手改动数据前的本地存档。实现必须保证「最多保留 N 份」且写入是原子的。 */
interface AiSnapshotStore {
    /** 用当前状态打一份快照，返回后快照已落盘。 */
    suspend fun capture(state: AttentionState)

    /** 当前存在的快照，按槽位升序。 */
    suspend fun snapshots(): List<AiSnapshot>

    /** 最近一次写入的那份；没有快照时为 null。 */
    suspend fun latest(): AiSnapshot?
}

/**
 * 环形文件快照：固定 [keep] 个槽位轮流覆盖，因此目录里最多同时存在 [keep] 份，
 * 第 [keep] + 1 次写入会覆盖最旧的那一份，而不是让文件无限增长。
 *
 * 命名空间与用户手动备份严格分开：这里的文件名是 `attention_ai_snapshot_<槽位>.json`，
 * 而导入前备份是 `attention_import_backup.json`（见 `FileImportBackupStore`）。
 * 两套文件互不读写，所以「恢复导入前备份」永远看不到、也永远覆盖不到 AI 快照。
 *
 * 快照内容就是备份协议本身（[AttentionBackupCodec.encode]），因此需要时可以用同一套
 * 导入流程恢复；它只序列化领域状态，而 API key 只存在于 BuildConfig / local.properties，
 * 从不进入 `AttentionState`，也就不可能进入快照。
 */
class FileAiSnapshotStore(
    private val directory: File,
    private val keep: Int = DEFAULT_KEEP,
) : AiSnapshotStore {
    init {
        require(keep in 1..MAX_KEEP) { "AI 快照保留份数必须在 1..$MAX_KEEP 之间，实际 $keep" }
    }

    /** 串行化「读槽位 → 写数据 → 写索引」，避免两次并发变更落到同一个槽位。 */
    private val mutex = Mutex()

    private val indexFile = File(directory, "$FILE_PREFIX.index")

    constructor(context: Context) : this(context.applicationContext.filesDir, DEFAULT_KEEP)

    override suspend fun capture(state: AttentionState) {
        val encoded = AttentionBackupCodec.encode(state)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val slot = nextSlot()
                writeAtomically(fileFor(slot), encoded)
                // 索引最后写：数据与索引之间断电时，最多重复用一个槽位，不会写出第 keep+1 份。
                writeAtomically(indexFile, slot.toString())
            }
        }
    }

    override suspend fun snapshots(): List<AiSnapshot> = withContext(Dispatchers.IO) {
        (1..keep).mapNotNull { slot ->
            val file = fileFor(slot)
            if (file.isFile) AiSnapshot(slot, file.readText(StandardCharsets.UTF_8)) else null
        }
    }

    override suspend fun latest(): AiSnapshot? = withContext(Dispatchers.IO) {
        val slot = currentSlot() ?: return@withContext null
        val file = fileFor(slot)
        if (file.isFile) AiSnapshot(slot, file.readText(StandardCharsets.UTF_8)) else null
    }

    private fun nextSlot(): Int = (currentSlot() ?: 0) % keep + 1

    private fun currentSlot(): Int? =
        indexFile.takeIf(File::isFile)
            ?.readText(StandardCharsets.UTF_8)
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in 1..keep }

    private fun fileFor(slot: Int): File = File(directory, "${FILE_PREFIX}_$slot.json")

    /** 与导入前备份相同的写法：先写临时文件，再原子改名，读到的永远是完整文件。 */
    private fun writeAtomically(target: File, text: String) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        Files.write(
            temporary.toPath(),
            text.toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        runCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrThrow()
    }

    companion object {
        /** 与 spec 一致：AI 快照滚动保留最近 3 份。 */
        const val DEFAULT_KEEP = 3

        /** 文件前缀；与手动备份的 `attention_import_backup` 刻意不同。 */
        const val FILE_PREFIX = "attention_ai_snapshot"

        private const val MAX_KEEP = 32
    }
}
