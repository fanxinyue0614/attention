package com.attention.app.fuguang.chat

import com.attention.app.fuguang.components.FuguangTextSegment

/**
 * 对话页的示例数据。
 *
 * 真实内容应当来自 Agent 运行结果与用户数据；在接入推理层之前，这里提供与
 * 「一周重排」原型第一屏一致的措辞，方便和规范 P11 的样张逐句对照。
 */
internal object FuguangChatDemoData {
    /** 名称槽：规范 P9 要求它是 prop，出厂值「浮光」，用户可改。 */
    const val agentName = "浮光"

    const val agentRole = "主 Agent · 已接入飞书"

    /** S2 目标胶囊只放数字，规范限制 6 字以内。 */
    const val goalLabel = "7.5"

    const val sessionStamp = "今天 21:47"

    const val userText =
        "周日临时有个去北京的面试，一整天都不在。概率论第三单元的习题和管理会计的复习得挪到周中，" +
            "但一定要在这门课上完之后。"

    /** C2 的强调用角色色而非加粗：规范 P11「强调用 main-deep，不加粗体」。 */
    val agentRecognition = FuguangTextSegment(
        text = "收到了。要挪的是三件事：概率论作业 3 小时、MA 笔记 1.5 小时，还有你每天的群面练习 40 分钟。",
        emphasis = listOf("概率论作业 3 小时", "MA 笔记 1.5 小时", "群面练习 40 分钟"),
    )

    val agentConstraint = FuguangTextSegment(
        text = "有个硬约束先跟你说：概率论的课在周五上午，所以那 3 小时只能在周五下午和周六，" +
            "周中塞不进去。周中剩的只有周三晚和周四。",
        emphasis = listOf("周五上午", "周五下午和周六", "周三晚和周四"),
    )

    /** C6 最多三条，单条不超过 14 字（超了组件会截断）。 */
    val suggestions = listOf("把这个月重排一下", "周四下午别排了", "先看看我有哪些目标")
}
