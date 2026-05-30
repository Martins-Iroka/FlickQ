package com.martdev.flickq.feature.showtime.presentation

import com.martdev.flickq.showtime.model.Showtime
import com.martdev.flickq.showtime.model.ShowtimeStatus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ShowtimeUi(
    val id: Long,
    val dateLabel: String,
    val timeLabel: String,
    val screenLabel: String,
    val priceLabel: String,
    val selectable: Boolean
)

fun Showtime.toShowtimeUi(): ShowtimeUi {
    val local = startsAt.toLocalDateTime(TimeZone.currentSystemDefault())
    return ShowtimeUi(
        id = id,
        dateLabel = "${local.dayOfWeek.shortName}, ${local.day} ${local.month.shortName}",
        timeLabel = "${local.hour.pad()}:${local.minute.pad()}",
        screenLabel = "Screen $roomId",
        priceLabel = "₦${price.grouped()}",
        selectable = status == ShowtimeStatus.SCHEDULED
    )
}

private fun Int.pad(): String = toString().padStart(2, '0')

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()

private val DayOfWeek.shortName: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

private val Month.shortName: String
    get() = when (this) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Feb"
        Month.MARCH -> "Mar"
        Month.APRIL -> "Apr"
        Month.MAY -> "May"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Aug"
        Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Oct"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dec"
    }
