package com.attention.app.data.audit

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 一次助手运行留下的审计摘要。
 *
 * 它回答的是「助手最近动过什么」，不是「某个目标的完整历史」：只记请求、终态、
 * 这次运行实际提交过的 command 名字，以及结束时刻。command 只留名字不留参数，
 * 笔记、备注这类正文因此不会进入审计轨迹。
 */
@Serializable
data class AgentTraceEntry(
    val request: String,
    val outcome: AgentRunOutcome,
    val commands: List<String> = emptyList(),
    val atEpochMillis: Long,
)

/**
 * 助手运行的终态，与 `:agent` 的 `AgentOutcome` 一一对应。
 *
 * `:app` 没有依赖 `:agent`（组合根在后续工作包里才把两者接起来），因此这里另立一个
 * 只用于持久化的枚举；接线时由包装层做一次映射，领域类型不会渗进存储格式。
 */
@Serializable
enum class AgentRunOutcome {
    @SerialName("answered")
    ANSWERED,

    @SerialName("budget_exhausted")
    BUDGET_EXHAUSTED,

    @SerialName("failed")
    FAILED,
}

/**
 * trace 的滚动语义：只保留最近 [maxEntries] 条，超出的从最旧开始淘汰。
 *
 * 单独抽成纯函数，是为了让「第 51 条进来时第 1 条消失」这条规则能在 JVM 测试里
 * 直接钉住，不必启动 DataStore。
 */
object AgentTraceRolling {
    /** 审计轨迹最多保留的运行条数。 */
    const val MAX_ENTRIES = 50

    fun append(
        existing: List<AgentTraceEntry>,
        entry: AgentTraceEntry,
        maxEntries: Int = MAX_ENTRIES,
    ): List<AgentTraceEntry> {
        require(maxEntries > 0) { "trace 容量必须为正，实际 $maxEntries" }
        return (existing + entry).takeLast(maxEntries)
    }
}

/**
 * audit 轨迹的读写接口。写入点只有一个：[record]，读出去的是按时间正序的最近若干条。
 */
interface AgentTraceStore {
    val entries: Flow<List<AgentTraceEntry>>

    /** 追加一条摘要，并按 [AgentTraceRolling.MAX_ENTRIES] 淘汰最旧的一条。 */
    suspend fun record(entry: AgentTraceEntry)

    /** 清空轨迹；目前只用于测试与将来的「清除审计记录」。 */
    suspend fun clear()
}

/** 把整份轨迹编码进一个 Preferences 值：一次 edit 就是一个原子提交。 */
private object AgentTraceCodec {
    private const val CURRENT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Envelope(
        val version: Int = CURRENT_VERSION,
        val entries: List<AgentTraceEntry> = emptyList(),
    )

    fun encode(entries: List<AgentTraceEntry>): String =
        json.encodeToString(Envelope.serializer(), Envelope(entries = entries))

    /**
     * 解不出来的一律当作空轨迹：trace 是诊断信息，坏一条不该让助手或设置页崩掉。
     * 数组里坏的单条会被跳过，其余仍然保留，因此半截写入不会让整段历史消失。
     */
    fun decode(encoded: String?): List<AgentTraceEntry> {
        if (encoded.isNullOrBlank()) return emptyList()
        val envelope = runCatching { json.parseToJsonElement(encoded).jsonObject }.getOrNull()
            ?: return emptyList()
        val entries = envelope["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(AgentTraceEntry.serializer(), element) }.getOrNull()
        }
    }
}

/**
 * DataStore 版本的审计轨迹。
 *
 * 轨迹不放进设置快照，也不放进 Room：它是随手可丢的诊断数据，用一个独立的
 * Preferences 文件最省事，且不会和业务事务互相拖累。
 */
class DataStoreAgentTraceStore(
    private val dataStore: DataStore<Preferences>,
) : AgentTraceStore {
    override val entries: Flow<List<AgentTraceEntry>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AgentTraceCodec.decode(preferences[Keys.ENTRIES]) }

    override suspend fun record(entry: AgentTraceEntry) {
        dataStore.edit { preferences ->
            val next = AgentTraceRolling.append(
                existing = AgentTraceCodec.decode(preferences[Keys.ENTRIES]),
                entry = entry,
            )
            preferences[Keys.ENTRIES] = AgentTraceCodec.encode(next)
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(Keys.ENTRIES) }
    }

    private object Keys {
        val ENTRIES = stringPreferencesKey("agent_trace_entries")
    }
}

private val Context.agentTraceDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_trace")

/** 组合根用的默认装配：轨迹单独一个 Preferences 文件，与设置互不影响。 */
fun Context.createAgentTraceStore(): AgentTraceStore = DataStoreAgentTraceStore(agentTraceDataStore)
