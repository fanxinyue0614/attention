package com.attention.app.agent

import com.attention.agent.llm.OpenAiCompatReasoner
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装配契约：三项配置缺任意一项就**不建推理器**，组合根因此回落到「助手不可用」，
 * 而不是拿半个配置去发请求（宁可不可用，不可静默降级）。
 */
class AgentModelConfigTest {
    @Test
    fun any_missing_setting_yields_no_reasoner() {
        listOf(
            Triple("", "glm-4-flash", "key"),
            Triple("https://open.bigmodel.cn/api/paas/v4", "", "key"),
            Triple("https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", ""),
        ).forEach { (baseUrl, model, apiKey) ->
            assertNull(
                "缺项时不应装配推理器：baseUrl=$baseUrl model=$model",
                buildOpenAiCompatReasoner(baseUrl, model, apiKey, emptyList(), emptyList()),
            )
        }
    }

    @Test
    fun a_complete_configuration_builds_the_openai_compat_reasoner() {
        val reasoner = buildOpenAiCompatReasoner(
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            model = "glm-4-flash",
            apiKey = "test-key",
            readOnlyTools = emptyList(),
            changeTools = emptyList(),
        )

        assertTrue(reasoner is OpenAiCompatReasoner)
    }
}
