package com.attention.app.fuguang.chat

import com.attention.agent.AgentOutcome
import com.attention.agent.ConfirmationToken
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 助手终态到答复气泡文本的映射契约。
 *
 * 答复必须原样透出(模型说了什么,用户就看什么);失败与预算耗尽翻译成
 * 用户能读懂、也讲清后果的话;待确认终态在确认卡片上线前一律告知「什么都没做」。
 */
class FuguangChatReplyTest {

    @Test
    fun an_answered_outcome_passes_the_model_text_through_untouched() {
        assertEquals(
            "已经记下 25 分钟阅读",
            AgentOutcome.Answered("已经记下 25 分钟阅读", emptyList()).toReplyText(),
        )
    }

    @Test
    fun a_failed_outcome_tells_the_user_it_did_not_complete_and_carries_the_reason() {
        val text = AgentOutcome.Failed("接口返回 HTTP 500", emptyList()).toReplyText()

        assertTrue(text.contains("没能完成"))
        assertTrue(text.contains("接口返回 HTTP 500"))
    }

    @Test
    fun a_budget_exhausted_outcome_suggests_a_more_specific_retry() {
        val text = AgentOutcome.BudgetExhausted(emptyList()).toReplyText()

        assertTrue(text.contains("预算用完"))
        assertTrue(text.contains("再试一次"))
    }

    @Test
    fun a_pending_confirmation_outcome_states_clearly_that_nothing_was_changed() {
        val outcome = AgentOutcome.PendingConfirmation(
            token = ConfirmationToken("t-1"),
            preview = buildJsonObject { },
            trace = emptyList(),
        )

        val text = outcome.toReplyText()

        assertTrue(text.contains("确认"))
        assertTrue(text.contains("没有做任何改动"))
    }
}
