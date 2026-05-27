package com.martdev.flickq.features.reservation.domain.service

import com.martdev.flickq.reservation.model.Reservation

interface ReservationService {
    suspend fun createReservation(userId: Long, showtimeId: Long, seatIds: List<Long>): Reservation
    suspend fun getReservationById(id: Long): Reservation                    // admin — no ownership check
    suspend fun getMyReservationById(id: Long, userId: Long): Reservation    // user — ownership enforced
    suspend fun getMyReservations(userId: Long): List<Reservation>
    suspend fun getAllReservations(limit: Int, offset: Long): List<Reservation>
    suspend fun confirmReservationFromPayment(id: Long): Reservation         // invoked by payment flow
    suspend fun cancelReservation(id: Long, userId: Long): Reservation       // user cancel (PENDING only)
    suspend fun cancelReservationAdmin(id: Long): Reservation                // admin cancel
    suspend fun cancelExpiredReservations()                                  // background job
}