package com.attention.app.agent

import com.attention.agent.AgentTool
import com.attention.agent.ChangeTool
import com.attention.agent.Reasoner
import com.attention.agent.llm.OpenAiCompatConfig
import com.attention.agent.llm.OpenAiCompatReasoner

/**
 * 由构建期注入的三项配置装配真模型推理器。
 *
 * 三项缺任意一项都返回 null：组合根据此回落到「助手不可用」的占位实现，
 * 而不是拿半个配置去发请求——与 #226「宁可不可用，不可静默降级」同一姿态。
 *
 * 纯函数、不读 BuildConfig，因此 JVM 测试可以直接钉住「缺项即 null」的契约；
 * 读 BuildConfig 的调用点在 [rememberWorkspaceAgentRuntime]。
 *
 * 工具清单由调用方传入：prompt 里描述的工具必须与目录里可派发的工具是同一份，
 * 否则模型会调用一个不存在的工具。
 */
internal fun buildOpenAiCompatReasoner(
    baseUrl: String,
    model: String,
    apiKey: String,
    readOnlyTools: List<AgentTool>,
    changeTools: List<ChangeTool>,
): Reasoner? {
    if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) return null
    return OpenAiCompatReasoner(
        config = OpenAiCompatConfig(baseUrl = baseUrl, model = model, apiKey = apiKey),
        readOnlyTools = readOnlyTools,
        changeTools = changeTools,
    )
}
