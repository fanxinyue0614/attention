package com.attention.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 一次行动请求：调用哪个工具、带什么参数。
 * 参数不合工具 schema 由循环转成错误观测，不由推理器预先保证。
 */
@Serializable
data class ActionRequest(val tool: String, val args: JsonObject = JsonObject(emptyMap()))

/** 推理器的一步决策：继续行动，或给出最终答复结束循环。 */
sealed class ReasonerDecision {
    /** 写下思考并调用一个工具。 */
    data class Act(val thought: String, val action: ActionRequest) : ReasonerDecision()

    /** 结束循环并给出最终答复；[thought] 可为空——有时答复本身不需要前置思考。 */
    data class Answer(val thought: String?, val text: String) : ReasonerDecision() {
        companion object {
            /** 不写前置思考、直接给出答复：调用点一眼可见这里不会有思考事件。 */
            fun withoutThought(text: String): Answer = Answer(thought = null, text = text)
        }
    }
}

/**
 * 推理器：读请求与事件轨迹，给出下一步决策。
 *
 * 挂起签名是下一阶段 LLM 绑定的真实形态——远程推理必然是挂起调用；
 * 现在就按真实形态立接口，避免届时改签名牵动所有调用方。
 * 可用工具清单由实现方在构造时持有（下一阶段序列化进 prompt），不进入本接口：
 * 循环只负责执行，推理器只负责决策，互不知道对方内部。
 */
fun interface Reasoner {
    suspend fun reason(request: String, history: List<AgentEvent>): ReasonerDecision
}
