package com.martdev.flickq.feature.reservation.presentation.myreservation

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.reservation.domain.MobileReservationRepository
import com.martdev.flickq.feature.reservation.presentation.ReservationTicketUI
import com.martdev.flickq.feature.reservation.presentation.toReservationTicketUI
import com.martdev.flickq.reservation.model.ReservationStatus
import kotlinx.coroutines.CancellationException

class MyReservationPagingSource(
    private val reservationRepository: MobileReservationRepository,
    private val status: ReservationStatus? = null
) : PagingSource<Long, ReservationTicketUI>() {
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, ReservationTicketUI> {
        val currentPage = params.key ?: 0
        return try {
            when(val result = reservationRepository.getReservationTickets(
                status, 5, currentPage.toInt()
            )) {
                is Result.Error -> LoadResult.Error(Exception(result.message))
                is Result.Success -> {
                    val tickets = result.data.reservationTickets.map {
                        it.toReservationTicketUI()
                    }
                    val next = result.data.nextOffset.takeIf { it >= 0 }
                    LoadResult.Page(
                        data = tickets,
                        prevKey = null,
                        nextKey = next
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, ReservationTicketUI>): Long? {
        return state.anchorPosition?.let {
            val anchorPage = state.closestPageToPosition(it)
            anchorPage?.prevKey?.plus(1)
        }
    }
}