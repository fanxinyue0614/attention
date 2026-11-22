package com.attention.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 脚本化推理器：按序吐出预设决策，并记录每次收到的请求与轨迹快照供断言。
 * 脚本耗尽即抛——这正好用来触发「推理器自身失败」的 Failed 路径，无需另一个替身。
 */
class FakeReasoner(vararg script: ReasonerDecision) : Reasoner {
    private val steps = script.toList()

    val requests = mutableListOf<String>()
    val histories = mutableListOf<List<AgentEvent>>()

    private var next = 0

    override suspend fun reason(request: String, history: List<AgentEvent>): ReasonerDecision {
        requests += request
        histories += history.toList()
        return steps.getOrElse(next++) { throw IllegalStateException("推理器脚本已耗尽（第 $next 次调用）") }
    }
}

/** 固定载荷的记录工具：执行即记录参数并返回预设载荷。 */
class RecordingTool(
    override val name: String = "echo",
    private val payload: JsonObject = buildJsonObject { },
) : AgentTool {
    override val description = "测试工具"
    override val parametersSchema = buildJsonObject { put("type", "object") }

    val calls = mutableListOf<JsonObject>()

    override fun execute(args: JsonObject): JsonObject {
        calls += args
        return payload
    }
}

/** 一执行就失败的工具，失败原因可注入，用来验证循环把异常转成错误观测。 */
class ThrowingTool(
    override val name: String = "boom",
    private val failure: Throwable = IllegalStateException("工具内部炸了"),
) : AgentTool {
    override val description = "测试工具"
    override val parametersSchema = buildJsonObject { put("type", "object") }

    override fun execute(args: JsonObject): JsonObject = throw failure
}
