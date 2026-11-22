package com.attention.agent.llm

import com.attention.agent.AgentEvent
import com.attention.agent.AgentTool
import com.attention.agent.ChangeTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** 一条 chat 消息。角色只有 system / user 两种：本客户端不保存对话，每次请求都是无状态重放。 */
@Serializable
data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
    }
}

/** 送进 prompt 的工具描述：只包含模型需要知道的三个事实，不暴露执行细节。 */
@Serializable
internal data class ToolDescriptor(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/**
 * 把「请求 + 本次运行的事件轨迹 + 可用工具」拼成 chat 消息。
 *
 * 隐私最小化的落点就在这里：请求是用户这次说的话，轨迹是本运行真实发生的思考、
 * 工具调用与工具返回；**没有任何全量领域状态**。模型想知道别的，必须通过只读工具去查，
 * 而那也是本次运行真实调用、真实返回的结果。
 *
 * 输出协议是严格的 JSON：模型每轮只能给一个对象，要么 `action` 要么 `answer`。
 * 非法输出由 [OpenAiCompatReasoner] 收敛为失败终态，不做猜测式容错。
 */
internal object AgentPrompt {
    private val json = Json { encodeDefaults = true }

    /** 只读工具与变更工具共用一个命名空间，清单按注册顺序拼接，便于 prompt 稳定复现。 */
    fun descriptors(readOnly: List<AgentTool>, change: List<ChangeTool>): List<ToolDescriptor> =
        readOnly.map { ToolDescriptor(it.name, it.description, it.parametersSchema) } +
            change.map { ToolDescriptor(it.name, it.description, it.parametersSchema) }

    fun messages(
        request: String,
        history: List<AgentEvent>,
        tools: List<ToolDescriptor>,
        nowText: String? = null,
    ): List<ChatMessage> = listOf(
        ChatMessage(ChatMessage.SYSTEM, systemPrompt(tools)),
        ChatMessage(ChatMessage.USER, userMessage(request, history, nowText)),
    )

    fun systemPrompt(tools: List<ToolDescriptor>): String = buildString {
        appendLine("你是「注意力」App 内置的时间管理助手，帮助用户查询和修改目标、时间记录与计时。")
        appendLine("你只能通过下面列出的工具读取或修改数据。")
        appendLine()
        appendLine("## 输出格式（必须严格遵守）")
        appendLine("整条回复必须是一个 JSON 对象：第一个字符是 {，最后一个字符是 }。")
        appendLine("不能有任何前言、解释、说明文字，也不能用 Markdown 代码块包裹。")
        appendLine("两种形态二选一：")
        appendLine()
        appendLine("1) 调用工具：")
        append("""{"thought":"一句话说明为什么调用","action":{"tool":"工具名","args":{}}}""")
        appendLine()
        appendLine("例如用户说「记 25 分钟阅读」：")
        append(
            """{"thought":"用户要补记 25 分钟阅读","action":{"tool":"record_time","args":{"action":"backfill","planningDate":"2026-11-21","durationMinutes":25}}}""",
        )
        appendLine()
        appendLine()
        appendLine("2) 直接回答：")
        append("""{"thought":"可选","answer":"给用户的答复"}""")
        appendLine()
        appendLine()
        appendLine("每一轮只输出一个 JSON 对象、只调用一个工具。工具的执行结果会出现在下一轮消息里。")
        appendLine()
        appendLine("## 可用工具")
        appendLine(renderTools(tools))
        appendLine("## 规则")
        appendLine("1. 只能使用上面列出的工具名与参数，不得编造。")
        appendLine("2. 需要任何数据（目标、时间记录、计时状态）时，必须先调用对应查询工具，不要凭空回答。")
        appendLine("3. 用户明确要求记录或修改时，直接调用对应工具，不要反问。")
        appendLine("4. 参数里需要 targetId 时，先调用 query_targets 查到真实 id 再用；**标题不是 id**，不要把标题填进 targetId。")
        appendLine("5. 参数必须符合工具的 JSON Schema；日期一律用 YYYY-MM-DD。")
        appendLine("6. 用中文回答用户。")
        appendLine("7. 用户没有明确要求的破坏性操作不要执行。")
        appendLine()
        appendLine("再次强调：只输出 JSON 对象本身，不要输出其他任何内容。")
    }

    fun userMessage(request: String, history: List<AgentEvent>, nowText: String? = null): String = buildString {
        appendLine("用户请求：")
        appendLine(request)
        if (!nowText.isNullOrBlank()) {
            appendLine()
            // 模型没有系统时钟：补记、查询都要落在具体日期上，今天几号必须在 prompt 里给出。
            appendLine("当前时间：$nowText")
        }
        appendLine()
        appendLine("本次运行已发生的事件（按时间顺序，JSON 数组；可能为空）：")
        append(encodeHistory(history))
    }

    private fun renderTools(tools: List<ToolDescriptor>): String =
        json.encodeToString(
            ListSerializer(ToolDescriptor.serializer()),
            tools,
        )

    private fun encodeHistory(history: List<AgentEvent>): String =
        json.encodeToString(ListSerializer(AgentEvent.serializer()), history)

    /** 粗略估算：4 个字符约 1 个 token。只用于预算控制，不追求精确。 */
    fun estimateTokens(text: String): Int = maxOf(1, (text.length + 3) / 4)

    /** 一整次请求的 prompt 估算（system + user），预算按它累加。 */
    fun estimateTokens(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateTokens(it.content) }
}

