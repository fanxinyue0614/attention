package com.attention.agent

import com.attention.domain.ActiveTimer
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.ScheduleEntry
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四个只读工具的投影口径：树汇总用 goalSummary、日程走 agendaTimeline、
 * 统计走 periodStats、计时走 elapsedMillis——全部经由注入的 now/zone 折算规划日，
 * 「凌晨 1 点仍算前一天」的界线规则必须在工具的默认日期里成立。
 */
class WorkspaceToolsTest {

    // 上海时间 2026-03-06 01:00；默认 03:00 界线意味着它仍属于 3 月 5 日的规划日
    private val now: Instant = Instant.parse("2026-03-05T17:00:00Z")
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun query_targets_projects_the_tree_with_one_time_stage_rollup() {
        val tool = toolNamed("query_targets")

        val payload = tool.execute(JsonObject(emptyMap())).jsonObject

        assertEquals(2, payload["count"]!!.jsonPrimitive.content.toInt())
        val targets = payload["targets"]!!.jsonArray.map { it.jsonObject }
        val root = targets.first { it["id"]!!.jsonPrimitive.content == "root" }
        // 子目标的 90 分钟按树汇总进父目标的一次性进度
        assertEquals("600", root["targetMinutes"]!!.jsonPrimitive.content)
        assertEquals("90", root["actualMinutes"]!!.jsonPrimitive.content)
        assertEquals("510", root["gapMinutes"]!!.jsonPrimitive.content)
        assertEquals("false", root["completed"]!!.jsonPrimitive.content)
        val child = targets.first { it["id"]!!.jsonPrimitive.content == "child" }
        assertEquals("root", child["parentId"]!!.jsonPrimitive.content)
        assertEquals("0", child["targetMinutes"]!!.jsonPrimitive.content)
    }

    @Test
    fun query_agenda_defaults_to_the_planning_day_which_crosses_the_calendar_midnight() {
        val tool = toolNamed("query_agenda")

        val payload = tool.execute(JsonObject(emptyMap())).jsonObject

        // now 的本地钟点是 03-06 01:00，但规划日界线把它折回 3 月 5 日
        assertEquals("2026-03-05", payload["planningDate"]!!.jsonPrimitive.content)
        val timed = payload["timed"]!!.jsonArray.map { it.jsonObject }
        assertEquals(1, timed.size)
        assertEquals("晨读", timed[0]["title"]!!.jsonPrimitive.content)
        assertEquals(1, payload["unscheduled"]!!.jsonArray.size)
        val capacity = payload["capacity"]!!.jsonObject
        assertEquals("60", capacity["usedMinutes"]!!.jsonPrimitive.content)
        assertEquals("false", capacity["overloaded"]!!.jsonPrimitive.content)
        assertNull(capacity["capacityMinutes"])
    }

    @Test
    fun query_agenda_accepts_an_explicit_day() {
        val tool = toolNamed("query_agenda")

        val payload = tool.execute(dayArgs("2026-03-06")).jsonObject

        assertEquals("2026-03-06", payload["planningDate"]!!.jsonPrimitive.content)
        assertEquals(0, payload["timed"]!!.jsonArray.size)
        assertEquals(0, payload["unscheduled"]!!.jsonArray.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun query_agenda_rejects_a_malformed_date() {
        toolNamed("query_agenda").execute(dayArgs("03/05/2026"))
    }

    @Test
    fun query_period_stats_projects_the_week_range_and_direct_vs_subtree_minutes() {
        val tool = toolNamed("query_period_stats")

        val payload = tool.execute(
            buildJsonObject { put("cadence", "WEEKLY") },
        ).jsonObject

        // 2026-03-05 是周四，周起点周一 → 03-02..03-08
        assertEquals("2026-03-02", payload["rangeStart"]!!.jsonPrimitive.content)
        assertEquals("2026-03-08", payload["rangeEnd"]!!.jsonPrimitive.content)
        assertEquals("90", payload["actualMinutes"]!!.jsonPrimitive.content)
        assertEquals("0", payload["unownedMinutes"]!!.jsonPrimitive.content)
        val byTarget = payload["byTarget"]!!.jsonArray.map { it.jsonObject }
        val child = byTarget.first { it["targetId"]!!.jsonPrimitive.content == "child" }
        assertEquals("90", child["directMinutes"]!!.jsonPrimitive.content)
        assertEquals("90", child["subtreeMinutes"]!!.jsonPrimitive.content)
        val root = byTarget.first { it["targetId"]!!.jsonPrimitive.content == "root" }
        assertEquals("0", root["directMinutes"]!!.jsonPrimitive.content)
        assertEquals("90", root["subtreeMinutes"]!!.jsonPrimitive.content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun query_period_stats_rejects_an_unknown_cadence() {
        toolNamed("query_period_stats").execute(buildJsonObject { put("cadence", "YEARLY") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun query_period_stats_requires_a_cadence() {
        toolNamed("query_period_stats").execute(JsonObject(emptyMap()))
    }

    @Test
    fun query_timer_reports_an_idle_workspace() {
        val tool = toolNamed("query_timer")

        val payload = tool.execute(JsonObject(emptyMap())).jsonObject

        assertEquals("false", payload["running"]!!.jsonPrimitive.content)
    }

    @Test
    fun query_timer_projects_the_running_timer_with_injected_now() {
        val running = AttentionState(
            targets = listOf(Target(id = "child", title = "红楼梦")),
            activeTimer = ActiveTimer(
                targetId = "child",
                startedAtEpochMillis = now.toEpochMilli() - 60_000,
                lastPlanningDate = "2026-03-05",
            ),
        )
        val paused = AttentionState(
            targets = listOf(Target(id = "child", title = "红楼梦")),
            activeTimer = ActiveTimer(
                targetId = "child",
                startedAtEpochMillis = now.toEpochMilli() - 60_000,
                accumulatedMillis = 30_000,
                paused = true,
                lastPlanningDate = "2026-03-05",
            ),
        )

        val runningPayload = tools(running).let { toolNamed("query_timer", it) }.execute(JsonObject(emptyMap())).jsonObject
        val pausedPayload = tools(paused).let { toolNamed("query_timer", it) }.execute(JsonObject(emptyMap())).jsonObject

        assertEquals("true", runningPayload["running"]!!.jsonPrimitive.content)
        assertEquals("红楼梦", runningPayload["targetTitle"]!!.jsonPrimitive.content)
        assertEquals("60000", runningPayload["elapsedMillis"]!!.jsonPrimitive.content)
        assertEquals("true", pausedPayload["paused"]!!.jsonPrimitive.content)
        // 暂停的计时冻结在累计值上，与 now 无关
        assertEquals("30000", pausedPayload["elapsedMillis"]!!.jsonPrimitive.content)
    }

    @Test
    fun query_agenda_marks_the_entry_that_is_currently_timing() {
        val timing = AttentionState(
            schedules = listOf(
                ScheduleEntry(id = "s1", planningDate = "2026-03-05", title = "晨读", startMinute = 480, endMinute = 540),
                ScheduleEntry(id = "s2", planningDate = "2026-03-05", title = "自由整理"),
            ),
            activeTimer = ActiveTimer(
                targetId = null,
                startedAtEpochMillis = now.toEpochMilli(),
                scheduleEntryId = "s2",
                lastPlanningDate = "2026-03-05",
            ),
        )

        val payload = tools(timing).let { toolNamed("query_agenda", it) }.execute(JsonObject(emptyMap())).jsonObject

        // 置顶规则：正在计时的条目即使没有开始时间也进时间轴第一行，并带 running 标记
        val timed = payload["timed"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, timed.size)
        assertEquals("s2", timed[0]["id"]!!.jsonPrimitive.content)
        assertEquals("true", timed[0]["running"]!!.jsonPrimitive.content)
        assertNull(timed[1]["running"])
    }

    @Test
    fun query_period_stats_declares_cadence_required_in_its_schema() {
        val schema = toolNamed("query_period_stats").parametersSchema

        // schema 是执行契约的镜像：execute 对 cadence 缺失抛异常，schema 就必须标注必填
        assertEquals(
            listOf("cadence"),
            schema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun all_four_tools_register_with_distinct_names_and_schemas() {
        val tools = tools()

        assertEquals(listOf("query_targets", "query_agenda", "query_period_stats", "query_timer"), tools.map { it.name })
        tools.forEach { tool ->
            assertEquals("object", tool.parametersSchema["type"]!!.jsonPrimitive.content)
            assertTrue(tool.description.isNotBlank())
        }
    }

    private fun toolNamed(name: String, source: List<AgentTool> = tools()): AgentTool =
        source.first { it.name == name }

    private fun tools(state: AttentionState = defaultState()): List<AgentTool> =
        WorkspaceTools(WorkspaceSource { WorkspaceView(state, now, zone) }).all()

    private fun dayArgs(date: String): JsonObject = buildJsonObject { put("planningDate", date) }

    private fun defaultState() = AttentionState(
        targets = listOf(
            Target(id = "root", title = "读书"),
            Target(id = "child", title = "红楼梦", parentId = "root"),
        ),
        goalStages = listOf(
            GoalStage(targetId = "root", cadence = GoalCadence.ONE_TIME, targetMinutes = 600, startDate = "2026-03-01"),
        ),
        timeEntries = listOf(
            TimeEntry(planningDate = "2026-03-05", durationMinutes = 90, targetId = "child"),
        ),
        schedules = listOf(
            ScheduleEntry(id = "s1", planningDate = "2026-03-05", title = "晨读", startMinute = 480, endMinute = 540),
            ScheduleEntry(id = "s2", planningDate = "2026-03-05", title = "自由整理"),
        ),
    )
}
