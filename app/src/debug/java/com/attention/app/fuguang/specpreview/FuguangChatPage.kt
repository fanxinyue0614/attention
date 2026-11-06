package com.attention.app.fuguang.specpreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.attention.app.fuguang.components.FuguangC2AiBubble
import com.attention.app.fuguang.components.FuguangC3UserBubble
import com.attention.app.fuguang.components.FuguangC4ThinkingBlock
import com.attention.app.fuguang.components.FuguangC6SuggestionCapsules
import com.attention.app.fuguang.components.FuguangC7TimestampDivider
import com.attention.app.fuguang.components.FuguangS1NavigationBar
import com.attention.app.fuguang.components.FuguangS3InputArea
import com.attention.app.fuguang.components.FuguangS5TabBar
import com.attention.app.fuguang.components.FuguangTextSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 屏 01「她先开口」：纯文字对话页。
 *
 * 按《浮光组件规范》P7 的隶属关系组装——S1 导航条 / 消息流（C7 · C3 · C2 · C4 · C6）/
 * S3 输入区（含 S4）/ S5 标签栏。页面不实现任何视觉数值：颜色、字号、尺寸全部来自
 * `FuguangDesignTokens` 的角色与契约，页面只负责「装在哪、什么时候出现」。
 *
 * 两处规范细节由这一层负责，而不是组件内部：
 * - C2 的 `showAvatar`：连续两条 AI 消息只有第一条带头像（P11「头像只在最左一条出现」）
 * - C6 只填不发：点建议胶囊只把句子写进输入框（P11「只填，不发」）
 *
 * 真机上这一步还接不上 Agent：答复是示例数据的固定回放，用来验收
 * 「发出去 → 思考 → 回来 → 给建议」这条交互骨架。接真实推理是后续任务。
 */
@Composable
internal fun FuguangChatPage(modifier: Modifier = Modifier) {
    val messages = remember {
        mutableStateListOf(
            ChatMessage.User(FuguangChatDemoData.userText),
            ChatMessage.Agent(FuguangChatDemoData.agentRecognition),
            ChatMessage.Agent(FuguangChatDemoData.agentConstraint),
        )
    }
    var composer by remember { mutableStateOf("") }
    // C4 的两态：null 表示没有思考块；loading=false 表示「已核对」的完成态
    var thinking by remember { mutableStateOf<ThinkingState?>(null) }
    var suggestions by remember { mutableStateOf(FuguangChatDemoData.suggestions) }
    var selectedTab by remember { mutableStateOf(CHAT_TAB_INDEX) }
    var replyIndex by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, thinking) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 页面自己吃系统栏与输入法内边距：导航条不被状态栏压住，
            // 输入区跟着键盘上移（配合 Manifest 的 adjustResize）。
            .systemBarsPadding()
            .imePadding(),
    ) {
        // S1 导航条（内含 S2 目标胶囊）
        FuguangS1NavigationBar(
            name = FuguangChatDemoData.agentName,
            roleLabel = FuguangChatDemoData.agentRole,
            goalLabel = FuguangChatDemoData.goalLabel,
            onMore = {},
        )

        // 消息流
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = PAGE_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FuguangC7TimestampDivider(FuguangChatDemoData.sessionStamp)
            messages.forEachIndexed { index, message ->
                when (message) {
                    is ChatMessage.User -> FuguangC3UserBubble(
                        text = message.text,
                        modifier = Modifier.testTag("message-$index"),
                    )

                    is ChatMessage.Agent -> FuguangC2AiBubble(
                        segments = listOf(message.segment),
                        // 连续 AI 消息只有首条带头像，与规范 P11 一致
                        showAvatar = index == 0 || messages[index - 1] !is ChatMessage.Agent,
                        modifier = Modifier.testTag("message-$index"),
                    )
                }
            }
            thinking?.let { state ->
                FuguangC4ThinkingBlock(text = state.label, loading = state.loading)
            }
            if (thinking == null) {
                FuguangC6SuggestionCapsules(
                    suggestions = suggestions,
                    onSelect = { picked ->
                        // 规范 C6：只把句子填进输入框，不发送
                        composer = picked
                    },
                )
            }
        }

        // S3 输入区（内含 S4 发送键）+ S5 标签栏
        Column(modifier = Modifier.padding(horizontal = PAGE_HORIZONTAL, vertical = 10.dp)) {
            FuguangS3InputArea(
                value = composer,
                onValueChange = { composer = it },
                onSend = {
                    val text = composer.trim()
                    if (text.isNotEmpty()) {
                        messages += ChatMessage.User(text)
                        composer = ""
                        suggestions = emptyList()
                        scope.launch {
                            thinking = ThinkingState(THINKING_PROGRESS, loading = true)
                            delay(THINKING_MILLIS)
                            thinking = ThinkingState(THINKING_DONE, loading = false)
                            delay(DONE_MILLIS)
                            messages += ChatMessage.Agent(nextReply(replyIndex++))
                            thinking = null
                            suggestions = FuguangChatDemoData.suggestions
                        }
                    }
                },
            )
            FuguangS5TabBar(selectedIndex = selectedTab, onSelect = { selectedTab = it })
        }
    }
}

/** 消息流里只有两种东西：用户说的话、Agent 说的话。卡片族不在这条链路上。 */
private sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Agent(val segment: FuguangTextSegment) : ChatMessage
}

/** C4 的两种状态：进行中（转圈）与完成（勾，文案不变灰）。 */
private data class ThinkingState(val label: String, val loading: Boolean)

/** 回放用的下一句答复，取自示例数据；用完之后停在最后一句，不伪造新内容。 */
private fun nextReply(index: Int): FuguangTextSegment = when (index % 2) {
    0 -> FuguangChatDemoData.agentRecognition
    else -> FuguangChatDemoData.agentConstraint
}

private const val THINKING_MILLIS = 900L
private const val DONE_MILLIS = 500L
private const val CHAT_TAB_INDEX = 0
private val PAGE_HORIZONTAL = 16.dp
private const val THINKING_PROGRESS = "正在核对这周的空档…"
private const val THINKING_DONE = "已核对 12 个空闲段"
