package com.martdev.flickq.feature.movie.presentation

import androidx.compose.material3.SelectableDates

class SelectableDateRange(private val minMillis: Long, private val year: Int) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        return utcTimeMillis >= minMillis
    }

    override fun isSelectableYear(year: Int): Boolean {
        return year == this.year
    }
}