package com.attention.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ReAct 循环的语义：答复即终止、观测必须回填、预算耗尽与一切失败都是结果值、
 * 只有协程取消可以穿透循环。这些都是调用方依赖的接口不变式（ADR-0008）。
 */
class ReActAgentTest {

    private fun agent(reasoner: Reasoner, vararg tools: AgentTool, maxSteps: Int = 8) =
        ReActAgent(reasoner, ToolCatalog(tools.toList()), maxSteps)

    @Test
    fun first_step_answer_ends_the_loop_without_any_tool_call() = runTest {
        val reasoner = FakeReasoner(ReasonerDecision.Answer.withoutThought("直接答复"))
        val tool = RecordingTool()

        val outcome = agent(reasoner, tool).run("现在几点")

        assertEquals(AgentOutcome.Answered("直接答复", listOf(AgentEvent.Final("直接答复"))), outcome)
        assertEquals(listOf("现在几点"), reasoner.requests)
        assertTrue(tool.calls.isEmpty())
    }

    @Test
    fun answer_with_a_thought_records_both_events() = runTest {
        val reasoner = FakeReasoner(ReasonerDecision.Answer("查完了", "答复"))

        val outcome = agent(reasoner).run("请求")

        assertEquals(
            AgentOutcome.Answered("答复", listOf(AgentEvent.Thought("查完了"), AgentEvent.Final("答复"))),
            outcome,
        )
    }

    @Test
    fun act_then_answer_records_the_full_timeline_and_feeds_the_observation_back() = runTest {
        val payload = buildJsonObject { put("value", 42) }
        val tool = RecordingTool(payload = payload)
        val reasoner = FakeReasoner(
            ReasonerDecision.Act("先查", ActionRequest("echo", buildJsonObject { put("q", "x") })),
            ReasonerDecision.Answer(null, "结果是 42"),
        )

        val outcome = agent(reasoner, tool).run("请求")

        assertEquals(
            AgentOutcome.Answered(
                "结果是 42",
                listOf(
                    AgentEvent.Thought("先查"),
                    AgentEvent.Action("echo", buildJsonObject { put("q", "x") }),
                    AgentEvent.Observation(payload),
                    AgentEvent.Final("结果是 42"),
                ),
            ),
            outcome,
        )
        // 观测回填是 ReAct 的核心：第二轮推理必须看得到第一轮的观测
        assertEquals(3, reasoner.histories[1].size)
        assertEquals(AgentEvent.Observation(payload), reasoner.histories[1].last())
        assertEquals(listOf<JsonObject>(buildJsonObject { put("q", "x") }), tool.calls)
    }

    @Test
    fun budget_exhaustion_returns_a_result_with_the_whole_trace() = runTest {
        val keepActing = ReasonerDecision.Act("还想再查", ActionRequest("echo"))
        val reasoner = FakeReasoner(keepActing, keepActing)

        val outcome = agent(reasoner, RecordingTool(), maxSteps = 2).run("请求")

        // 每轮 Thought + Action + Observation 三条事件，两轮共六条
        assertTrue(outcome is AgentOutcome.BudgetExhausted)
        assertEquals(6, (outcome as AgentOutcome.BudgetExhausted).trace.size)
        assertEquals(2, reasoner.requests.size)
    }

    @Test
    fun unknown_tool_becomes_an_error_observation_the_reasoner_can_recover_from() = runTest {
        val reasoner = FakeReasoner(
            ReasonerDecision.Act("记错了名字", ActionRequest("nope")),
            ReasonerDecision.Answer(null, "改口答复"),
        )

        val outcome = agent(reasoner, RecordingTool()).run("请求")

        assertEquals("改口答复", (outcome as AgentOutcome.Answered).text)
        val observation = reasoner.histories[1].last() as AgentEvent.Observation
        assertTrue(observation.isError)
        assertEquals(JsonPrimitive("未知工具：nope"), observation.payload["error"])
    }

    @Test
    fun tool_failure_becomes_an_error_observation_the_reasoner_can_recover_from() = runTest {
        val reasoner = FakeReasoner(
            ReasonerDecision.Act("它坏了", ActionRequest("boom")),
            ReasonerDecision.Answer(null, "绕过去了"),
        )

        val outcome = agent(reasoner, ThrowingTool()).run("请求")

        assertEquals("绕过去了", (outcome as AgentOutcome.Answered).text)
        val observation = reasoner.histories[1].last() as AgentEvent.Observation
        assertTrue(observation.isError)
        assertEquals(JsonPrimitive("工具内部炸了"), observation.payload["error"])
    }

    @Test
    fun bad_arguments_are_handled_like_any_other_tool_failure() = runTest {
        // 工具自己校验参数并抛 IllegalArgumentException；
        // 对循环来说这与任何其他工具失败是同一件事：错误观测，回填自纠。
        val reasoner = FakeReasoner(
            ReasonerDecision.Act("参数不对", ActionRequest("strict")),
            ReasonerDecision.Answer(null, "已纠正"),
        )

        val outcome = agent(reasoner, ThrowingTool(name = "strict", failure = IllegalArgumentException("参数不合法"))).run("请求")

        assertTrue(outcome is AgentOutcome.Answered)
        val observation = reasoner.histories[1].last() as AgentEvent.Observation
        assertTrue(observation.isError)
        assertEquals(JsonPrimitive("参数不合法"), observation.payload["error"])
    }

    @Test
    fun reasoner_failure_becomes_a_failed_result_that_keeps_the_trace_so_far() = runTest {
        val reasoner = FakeReasoner(
            ReasonerDecision.Act("先查一次", ActionRequest("echo")),
        )

        val outcome = agent(reasoner, RecordingTool()).run("请求")

        // 脚本第二轮耗尽 → Failed；失败前的三条事件全部保留
        assertTrue(outcome is AgentOutcome.Failed)
        assertTrue((outcome as AgentOutcome.Failed).reason.contains("耗尽"))
        assertEquals(3, outcome.trace.size)
    }

    @Test
    fun cancellation_is_never_swallowed_into_a_result() = runTest {
        val reasoner = Reasoner { _, _ -> throw CancellationException("调用方要停") }

        val thrown = runCatching { agent(reasoner).run("请求") }

        assertNull(thrown.getOrNull())
        assertTrue(thrown.exceptionOrNull() is CancellationException)
    }

    @Test
    fun trace_survives_a_json_round_trip() {
        val trace = listOf(
            AgentEvent.Thought("想"),
            AgentEvent.Action("echo", buildJsonObject { put("q", "x") }),
            AgentEvent.Observation(buildJsonObject { put("value", 42) }),
            AgentEvent.Observation(buildJsonObject { put("error", "炸了") }, isError = true),
            AgentEvent.Final("完"),
        )

        val encoded = Json.encodeToString(trace)

        assertEquals(trace, Json.decodeFromString<List<AgentEvent>>(encoded))
    }

    @Test
    fun outcomes_survive_a_json_round_trip() {
        val trace = listOf(AgentEvent.Final("完"))
        val outcomes = listOf<AgentOutcome>(
            AgentOutcome.Answered("完", trace),
            AgentOutcome.BudgetExhausted(trace),
            AgentOutcome.Failed("网络炸了", trace),
        )

        val encoded = Json.encodeToString(outcomes)

        assertEquals(outcomes, Json.decodeFromString<List<AgentOutcome>>(encoded))
    }

    @Test(expected = IllegalArgumentException::class)
    fun non_positive_budget_is_rejected_at_construction() {
        ReActAgent(FakeReasoner(), ToolCatalog(emptyList()), maxSteps = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicate_tool_names_are_rejected_at_construction() {
        ToolCatalog(listOf(RecordingTool(name = "dup"), RecordingTool(name = "dup")))
    }
}
