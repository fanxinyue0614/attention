package com.attention.app.fuguang

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attention.agent.AgentOutcome
import com.attention.app.fuguang.chat.AgentConversation
import com.attention.app.fuguang.chat.FuguangChatPage
import com.attention.app.fuguang.components.FuguangTheme
import com.attention.app.ui.theme.AttentionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真模型模式下对话页与运行时终态的接线:
 * 发送的用户请求原样到达会话入口,终态映射成答复气泡;
 * 状态未加载完时请求被挡在本地,不打到会话入口。
 */
@RunWith(AndroidJUnit4::class)
class FuguangChatConversationTest {
    @get:Rule
    val rule = createComposeRule()

    private class ScriptedConversation(private val reply: (String) -> AgentOutcome) : AgentConversation {
        val requests = mutableListOf<String>()

        override suspend fun ask(request: String): AgentOutcome {
            requests += request
            return reply(request)
        }
    }

    @Test
    fun a_send_request_reaches_the_conversation_entry_and_the_answer_becomes_a_bubble() {
        val conversation = ScriptedConversation { AgentOutcome.Answered("已经记下 25 分钟阅读", emptyList()) }
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme { FuguangChatPage(conversation = conversation, stateReady = true) }
            }
        }

        rule.onNodeWithTag("input-s3").performTextInput("帮我记 25 分钟阅读")
        rule.onNodeWithTag("send-s4").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("已经记下 25 分钟阅读").fetchSemanticsNodes().isNotEmpty
        }

        assertEquals(listOf("帮我记 25 分钟阅读"), conversation.requests)
        rule.onNodeWithText("帮我记 25 分钟阅读").assertIsDisplayed()
    }

    @Test
    fun a_failed_outcome_becomes_a_bubble_that_explains_the_failure() {
        val conversation = ScriptedConversation { AgentOutcome.Failed("接口返回 HTTP 500", emptyList()) }
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme { FuguangChatPage(conversation = conversation, stateReady = true) }
            }
        }

        rule.onNodeWithTag("input-s3").performTextInput("查一下目标")
        rule.onNodeWithTag("send-s4").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("我没能完成这次请求:接口返回 HTTP 500").fetchSemanticsNodes().isNotEmpty
        }
    }

    @Test
    fun a_request_is_kept_local_while_the_state_has_not_loaded() {
        val conversation = ScriptedConversation { AgentOutcome.Answered("不该出现在这里", emptyList()) }
        rule.setContent {
            AttentionTheme(darkTheme = false) {
                FuguangTheme { FuguangChatPage(conversation = conversation, stateReady = false) }
            }
        }

        rule.onNodeWithTag("input-s3").performTextInput("帮我记 25 分钟阅读")
        rule.onNodeWithTag("send-s4").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("你的数据还在读取").fetchSemanticsNodes().isNotEmpty
        }

        assertEquals(emptyList<String>(), conversation.requests)
    }
}
