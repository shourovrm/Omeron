package com.omeron.ui.profile

import com.omeron.data.model.MediaType
import com.omeron.data.model.PostType
import com.omeron.data.model.PosterType
import com.omeron.data.model.Sort
import com.omeron.data.model.Sorting
import com.omeron.data.model.db.PostEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryGroupingTest {

    private val timeZone = TimeZone.getTimeZone("UTC")

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
    }

    private fun postViewedAt(id: String, viewedAt: Long) = PostEntity(
        id = id,
        subreddit = "r/test",
        title = "Title $id",
        ratio = 100,
        totalAwards = 0,
        isOC = false,
        score = "1",
        type = PostType.TEXT,
        domain = "self.test",
        isSelf = true,
        selfTextHtml = null,
        suggestedSorting = Sorting(Sort.BEST),
        isOver18 = false,
        preview = null,
        isSpoiler = false,
        isArchived = false,
        isLocked = false,
        posterType = PosterType.REGULAR,
        author = "author",
        commentsNumber = "0",
        permalink = "/r/test/comments/$id",
        isStickied = false,
        url = "https://example.com/$id",
        created = 0L,
        mediaType = MediaType.NO_MEDIA,
        mediaUrl = "",
        time = viewedAt
    )

    @Test
    fun `no posts gives no items`() {
        val now = millisAt(2026, Calendar.OCTOBER, 8, 12)

        assertTrue(groupHistoryByDay(emptyList(), now, timeZone).isEmpty())
    }

    @Test
    fun `posts are grouped under today, yesterday and a date`() {
        val now = millisAt(2026, Calendar.OCTOBER, 8, 12)
        val posts = listOf(
            postViewedAt("a", millisAt(2026, Calendar.OCTOBER, 8, 11)),
            postViewedAt("b", millisAt(2026, Calendar.OCTOBER, 8, 1)),
            postViewedAt("c", millisAt(2026, Calendar.OCTOBER, 7, 23)),
            postViewedAt("d", millisAt(2026, Calendar.OCTOBER, 3, 9))
        )

        val items = groupHistoryByDay(posts, now, timeZone)

        assertEquals(
            listOf(
                HistoryListItem.DayLabel(HistoryDay.Today, showsClearAction = true),
                HistoryListItem.Row(posts[0]),
                HistoryListItem.Row(posts[1]),
                HistoryListItem.DayLabel(HistoryDay.Yesterday, showsClearAction = false),
                HistoryListItem.Row(posts[2]),
                HistoryListItem.DayLabel(
                    HistoryDay.Date(millisAt(2026, Calendar.OCTOBER, 3, 0)),
                    showsClearAction = false
                ),
                HistoryListItem.Row(posts[3])
            ),
            items
        )
    }

    @Test
    fun `first label is yesterday when nothing was viewed today`() {
        val now = millisAt(2026, Calendar.OCTOBER, 8, 12)
        val posts = listOf(postViewedAt("a", millisAt(2026, Calendar.OCTOBER, 7, 8)))

        val items = groupHistoryByDay(posts, now, timeZone)

        assertEquals(
            HistoryListItem.DayLabel(HistoryDay.Yesterday, showsClearAction = true),
            items.first()
        )
    }

    @Test
    fun `yesterday is found across a month boundary`() {
        val now = millisAt(2026, Calendar.NOVEMBER, 1, 10)
        val posts = listOf(postViewedAt("a", millisAt(2026, Calendar.OCTOBER, 31, 22)))

        val items = groupHistoryByDay(posts, now, timeZone)

        assertEquals(HistoryDay.Yesterday, (items.first() as HistoryListItem.DayLabel).day)
    }
}
