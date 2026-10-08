package com.omeron.ui.profile

import com.omeron.data.model.db.PostEntity
import java.util.Calendar
import java.util.TimeZone

/** The day a group of history rows was viewed on. */
sealed class HistoryDay {
    object Today : HistoryDay()

    object Yesterday : HistoryDay()

    /** Any earlier day, identified by the start of that day in the device's time zone. */
    data class Date(val startOfDayMillis: Long) : HistoryDay()
}

sealed class HistoryListItem {

    /** [showsClearAction] is true on the first label only, which carries "Clear history". */
    data class DayLabel(val day: HistoryDay, val showsClearAction: Boolean) : HistoryListItem()

    data class Row(val post: PostEntity) : HistoryListItem()
}

/**
 * Puts a day label before each run of posts viewed on the same day. [posts] must be ordered
 * newest first, which is how the history table returns them; the view time is [PostEntity.time].
 */
fun groupHistoryByDay(
    posts: List<PostEntity>,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): List<HistoryListItem> {
    val startOfToday = startOfDay(nowMillis, timeZone)
    val startOfYesterday = startOfDay(startOfToday - 1, timeZone)

    val items = mutableListOf<HistoryListItem>()
    var currentDayStart: Long? = null
    for (post in posts) {
        val postDayStart = startOfDay(post.time, timeZone)
        if (postDayStart != currentDayStart) {
            currentDayStart = postDayStart
            val day = when (postDayStart) {
                startOfToday -> HistoryDay.Today
                startOfYesterday -> HistoryDay.Yesterday
                else -> HistoryDay.Date(postDayStart)
            }
            items.add(HistoryListItem.DayLabel(day, showsClearAction = items.isEmpty()))
        }
        items.add(HistoryListItem.Row(post))
    }
    return items
}

// Calendar rather than dividing by 24 hours, because a day is 23 or 25 hours long when the
// clocks change.
private fun startOfDay(timeMillis: Long, timeZone: TimeZone): Long {
    return Calendar.getInstance(timeZone).apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
