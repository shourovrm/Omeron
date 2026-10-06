package com.omeron.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun scrapedDatetimeIsReadInTheGivenZoneNotTheDeviceZone() {
        val date = DateUtil.getDateFromString(
            "yyyy-MM-dd'T'HH:mm:ss",
            "2026-06-13T09:47:04+00:00",
            TimeZone.getTimeZone("UTC")
        )

        // 2026-06-13T09:47:04Z
        assertEquals(1781344024000L, date?.time)
    }
}
