package com.attention.agent.llm

import com.attention.agent.AgentEvent
import com.attention.agent.AgentTool
import com.attention.agent.ChangeTool
import com.attention.agent.Reasoner
import com.attention.agent.ReasonerDecision
import com.attention.agent.TokenBudgetExceededException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 网络侧失败（连接、超时、HTTP 状态码）统一收敛成这个值，消息里只有可安全记录的事实：
 * 异常类型、HTTP 状态码。密钥在请求头里，从不进入任何消息。
 */
class OpenAiCompatFailureException(message: String) : Exception(message)

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    /** OpenAI 兼容的 JSON 模式；不支持的端点会忽略或 400，由调用方决定是否降级。 */
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
)

@Serializable
internal data class ResponseFormat(val type: String) {
    companion object {
        val JSON_OBJECT = ResponseFormat("json_object")
    }
}

@Serializable
internal data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList(),
)

@Serializable
internal data class ChatCompletionChoice(
    val message: ChatCompletionMessage = ChatCompletionMessage(),
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
internal data class ChatCompletionMessage(
    val content: String = "",
)

/**
 * OpenAI 兼容 `/chat/completions` 的推理器实现。
 *
 * 三条不变式：
 * - **错误值化**：网络失败、超时、4xx/5xx、非 JSON、缺字段全部抛异常，由 ReAct 循环
 *   统一收敛为 Failed 终态——调用方永远不需要围绕模型调用写 try/catch。
 * - **隐私最小化**：请求体只有系统提示（含工具清单）、用户请求与本次运行的事件轨迹；
 *   没有任何全量领域状态。想知道状态，模型必须调只读工具，那也是本次运行真实返回的结果。
 * - **预算前置**：每次发送前按字符估算 prompt token，与本次运行已用累加；即将超限就不发请求，
 *   抛 [TokenBudgetExceededException] 让循环收敛为 BudgetExhausted。
 *
 * 密钥只用于 `Authorization` 头；[OpenAiCompatConfig.toString] 已脱敏，异常消息只写状态码与
 * 异常类型，绝不回显请求头或请求体。
 */
class OpenAiCompatReasoner(
    private val config: OpenAiCompatConfig,
    readOnlyTools: List<AgentTool> = emptyList(),
    changeTools: List<ChangeTool> = emptyList(),
    /** 时钟与时区每次请求现取：模型需要知道「今天几号」，不能停在构造那一刻。 */
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val client: OkHttpClient = defaultHttpClient(config),
) : Reasoner {
    private val tools = AgentPrompt.descriptors(readOnlyTools, changeTools)
    private val json = Json { ignoreUnknownKeys = true }

    /** 一次运行内串行累加；运行边界以「轨迹为空」判定——循环每轮都从空轨迹开始。 */
    private val mutex = Mutex()
    private var usedPromptTokens = 0

    override suspend fun reason(request: String, history: List<AgentEvent>): ReasonerDecision =
        mutex.withLock {
            if (history.isEmpty()) usedPromptTokens = 0

            val messages = AgentPrompt.messages(request, history, tools, nowText = currentTimeText())
            val estimate = AgentPrompt.estimateTokens(messages)
            if (usedPromptTokens + estimate > config.promptTokenBudget) {
                throw TokenBudgetExceededException(
                    estimatedTokens = usedPromptTokens + estimate,
                    budget = config.promptTokenBudget,
                )
            }

            val content = withContext(Dispatchers.IO) { complete(messages) }
            usedPromptTokens += estimate
            val decision = runCatching { DecisionParser.parse(content) }.getOrNull()
            if (decision != null) return@withLock decision

            // 小模型偶尔不守 JSON 格式。给它一次修复机会：把不合法的那条回复原样带回去，
            // 明确要求「只输出 JSON」。仍不合法才交回循环收敛为 Failed——不猜测、不拼凑。
            val repaired = repairMessages(messages, content)
            val repairedEstimate = AgentPrompt.estimateTokens(repaired)
            if (usedPromptTokens + repairedEstimate > config.promptTokenBudget) {
                throw TokenBudgetExceededException(
                    estimatedTokens = usedPromptTokens + repairedEstimate,
                    budget = config.promptTokenBudget,
                )
            }
            val second = withContext(Dispatchers.IO) { complete(repaired) }
            usedPromptTokens += repairedEstimate
            DecisionParser.parse(second)
        }

    /**
     * 修复回合的消息：保留原对话，把不合法的回复作为 assistant 轮带回，再用一条 user 指令
     * 要求只输出 JSON。截断到 2000 字符，避免把超长废话整段再发一遍。
     */
    private fun repairMessages(original: List<ChatMessage>, badReply: String): List<ChatMessage> =
        original + listOf(
            ChatMessage("assistant", badReply.take(2_000)),
            ChatMessage(ChatMessage.USER, "你上面的回复不是合法 JSON。请只输出一个 JSON 对象，不要有任何其他文字或代码块标记。"),
        )

    /** 发一次请求，返回模型消息正文；一切失败都折算成上面两类异常。 */
    private fun complete(messages: List<ChatMessage>): String {
        val payload = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = config.model,
                messages = messages,
                // 强约束：模型必须回一个 JSON 对象。实测 glm-4-flash 不加这个会夹带解释文字。
                responseFormat = ResponseFormat.JSON_OBJECT,
            ),
        )
        val request = Request.Builder()
            .url(config.chatCompletionsUrl)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val (code, body) = try {
            client.newCall(request).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        } catch (failure: IOException) {
            // 只保留异常类型：超时与连接失败的原始消息可能带 URL，没有必要进错误值。
            throw OpenAiCompatFailureException("网络请求失败：${failure.javaClass.simpleName}")
        }

        if (code !in 200..299) {
            throw OpenAiCompatFailureException("接口返回 HTTP $code")
        }
        if (body.isBlank()) {
            throw InvalidModelResponseException("接口返回空响应体")
        }

        val parsed = runCatching {
            json.decodeFromString(ChatCompletionResponse.serializer(), body)
        }.getOrNull() ?: throw InvalidModelResponseException("接口响应不是合法的 chat completion JSON")

        return parsed.choices.firstOrNull()
            ?.message
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidModelResponseException("接口响应缺少 choices[0].message.content")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /** 给模型看的「现在」：日期 + 星期 + 时刻 + 时区，让补记与查询落在正确日期上。 */
    private fun currentTimeText(): String =
        TIME_FORMATTER.format(now().atZone(zone()))
}

/** 按配置建客户端：超时来自配置，读超时故意给得比连接久（模型生成本来就慢）。 */
internal fun defaultHttpClient(config: OpenAiCompatConfig): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

/** 2026-11-21 星期六 22:40 这种形态；中文模型对这个格式最不容易读错。 */
private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", Locale.CHINA)
