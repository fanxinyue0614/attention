package com.attention.app.agent

import com.attention.agent.AgentOutcome
import com.attention.agent.CommandExecutor
import com.attention.agent.WorkspaceSource
import com.attention.agent.WorkspaceView
import com.attention.app.BuildConfig
import com.attention.app.data.audit.AiChangeAudit
import com.attention.domain.AttentionCommand
import com.attention.domain.AttentionCommandResult
import com.attention.domain.AttentionState
import com.attention.domain.Target
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真模型联调（不在 CI 跑）：用 debug 变体注入的配置打一次真实请求，
 * 验证「请求 → 模型 → 工具 → 领域 command 派发」整条链路是通的。
 *
 * 没有配置模型时自动跳过——CI 上 LLM_API_KEY 为空，这些用例不会红，
 * 也不需要把密钥交给 CI。本地想跑：把三项填进仓库根 local.properties 即可。
 */
class AgentChainSmokeTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」：2026-11-21 22:40（上海），让补记日期可预期。 */
    private val now: Instant = Instant.parse("2026-11-21T14:40:00Z")

    private val state = AttentionState(targets = listOf(Target(id = "t-ielts", title = "雅思")))

    @Test
    fun a_real_model_turns_a_request_into_a_dispatched_time_entry() = runBlocking {
        assumeTrue(
            "未配置模型（local.properties / 环境变量），跳过真实链路联调",
            BuildConfig.LLM_API_KEY.isNotBlank(),
        )
        val source = WorkspaceSource { WorkspaceView(state, now, zone) }
        val readOnlyTools = defaultReadOnlyTools(source)
        val changeTools = defaultChangeTools(source)
        val reasoner = requireNotNull(
            buildOpenAiCompatReasoner(
                baseUrl = BuildConfig.LLM_BASE_URL,
                model = BuildConfig.LLM_MODEL,
                apiKey = BuildConfig.LLM_API_KEY,
                readOnlyTools = readOnlyTools,
                changeTools = changeTools,
            ),
        )
        val dispatched = mutableListOf<AttentionCommand>()

        val runtime = WorkspaceAgentRuntime(
            dispatch = CommandExecutor { command, _, _ ->
                dispatched += command
                AttentionCommandResult(state)
            },
            audit = AiChangeAudit(RecordingTraceStore(), RecordingSnapshotStore()),
            state = { state },
            now = { now },
            zone = { zone },
            reasoner = reasoner,
            readOnlyTools = readOnlyTools,
            changeTools = changeTools,
            maxSteps = 8,
        )

        val outcome = runtime.run("帮我记录 25 分钟阅读时间")
        val answerText = (outcome as? AgentOutcome.Answered)?.text

        assertTrue(
            "模型应给出答复，实际终态：$outcome",
            outcome is AgentOutcome.Answered,
        )
        assertTrue(
            "应派发一条补记时间的领域 command；模型答复为「$answerText」，实际派发：$dispatched",
            dispatched.any { it is AttentionCommand.AddManualTimeEntry },
        )
    }

    @Test
    fun a_read_only_question_does_not_dispatch_any_change() = runBlocking {
        assumeTrue(
            "未配置模型（local.properties / 环境变量），跳过真实链路联调",
            BuildConfig.LLM_API_KEY.isNotBlank(),
        )
        val source = WorkspaceSource { WorkspaceView(state, now, zone) }
        val readOnlyTools = defaultReadOnlyTools(source)
        val changeTools = defaultChangeTools(source)
        val reasoner = requireNotNull(
            buildOpenAiCompatReasoner(
                baseUrl = BuildConfig.LLM_BASE_URL,
                model = BuildConfig.LLM_MODEL,
                apiKey = BuildConfig.LLM_API_KEY,
                readOnlyTools = readOnlyTools,
                changeTools = changeTools,
            ),
        )
        val dispatched = mutableListOf<AttentionCommand>()

        val runtime = WorkspaceAgentRuntime(
            dispatch = CommandExecutor { command, _, _ ->
                dispatched += command
                AttentionCommandResult(state)
            },
            audit = AiChangeAudit(RecordingTraceStore(), RecordingSnapshotStore()),
            state = { state },
            now = { now },
            zone = { zone },
            reasoner = reasoner,
            readOnlyTools = readOnlyTools,
            changeTools = changeTools,
            maxSteps = 6,
        )

        val outcome = runtime.run("请调用 query_targets 查一下我有哪些目标")

        assertTrue("模型应给出答复，实际终态：$outcome", outcome is AgentOutcome.Answered)
        assertTrue("只读提问不应派发任何变更，实际：$dispatched", dispatched.isEmpty())
    }
}
