package com.martdev.flickq.plugins

import com.martdev.flickq.auth.request.CreateUserRequest
import com.martdev.flickq.auth.request.RefreshTokenRequest
import com.martdev.flickq.auth.request.ResendOTPRequest
import com.martdev.flickq.auth.request.UserLoginRequest
import com.martdev.flickq.auth.request.UserVerificationRequest
import com.martdev.flickq.movie.GenreDTO
import com.martdev.flickq.movie.MovieDTO
import com.martdev.flickq.reservation.CreateReservationRequest
import com.martdev.flickq.room.RoomDTO
import com.martdev.flickq.room.SeatDTO
import com.martdev.flickq.showtime.ShowtimeDTO
import com.martdev.flickq.showtime.UpdateShowtimeStatusRequest
import com.martdev.flickq.showtime.model.ShowtimeStatus
import com.martdev.flickq.validation.Validator
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.enums.enumEntries

fun Application.configureRequestValidation() {
    val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+[a-zA-Z]{2,}$")
    install(RequestValidation) {
        validate<CreateUserRequest> { request ->
            val emailValidation = Validator.validateEmail(request.email)
            val passwordValidation = Validator.validatePassword(request.password)
            when {
                !emailValidation.isValid -> invalidResponseResult(emailValidation.errorMessage ?: "Invalid email")
                !passwordValidation.isValid -> invalidResponseResult(
                    passwordValidation.errorMessage ?: "Invalid password"
                )
                else -> ValidationResult.Valid
            }
        }

        validate<UserVerificationRequest> { request ->
            when {
                request.code.isEmpty() || request.code.length != 6 -> invalidResponseResult("Code is not valid")
                request.emailId.isEmpty() -> invalidResponseResult("Email id is needed")
                request.token.isEmpty() -> invalidResponseResult("Token is needed")
                else -> ValidationResult.Valid
            }
        }

        validate<UserLoginRequest> { request ->
            val emailValidation = Validator.validateEmail(request.email)
            val passwordValidation = Validator.validatePassword(request.password)
            when {
                !emailValidation.isValid -> invalidResponseResult(emailValidation.errorMessage ?: "Invalid email")
                !passwordValidation.isValid -> invalidResponseResult(
                    passwordValidation.errorMessage ?: "Invalid password"
                )
                else -> ValidationResult.Valid
            }
        }

        validate<ResendOTPRequest> { request ->
            if (request.email.isEmpty() || !emailPattern.matches(request.email)) {
                ValidationResult.Invalid("Invalid email")
            } else ValidationResult.Valid
        }

        validate<RefreshTokenRequest> { request ->
            if (request.refreshToken.isEmpty()) {
                ValidationResult.Invalid("Invalid refresh token")
            } else ValidationResult.Valid
        }

        validate<MovieDTO> { request ->
            when {
                request.title.isEmpty() -> invalidResponseResult("Title is required")
                request.description.isEmpty() -> invalidResponseResult("Description is required")
                request.posterUrl.isEmpty() -> invalidResponseResult("Poster URL is required")
                request.duration <= 0 -> invalidResponseResult("Duration is required")
                request.genres.isEmpty() -> invalidResponseResult("Movie genre is requred")
                else -> ValidationResult.Valid
            }
        }

        validate<GenreDTO> { request ->
            if (request.name.isEmpty()) {
                invalidResponseResult("Genre name required")
            } else ValidationResult.Valid
        }

        validate<RoomDTO> { request ->
            when {
                request.name.isEmpty() -> invalidResponseResult("Room name is required")
                request.rows <= 0 -> invalidResponseResult("A number of rows are required")
                request.columns <= 0 -> invalidResponseResult("A number of columns are required")
                else -> ValidationResult.Valid
            }
        }

        validate<SeatDTO> { request ->
            when {
                request.roomId <= 0 -> invalidResponseResult("Room id is required")
                request.rowLabel.isEmpty() -> invalidResponseResult("Row label is required")
                request.seatNumber <= 0 -> invalidResponseResult("Seat number is required")
                else -> ValidationResult.Valid
            }
        }

        val showtimeStatusErrorMessage =
            "Invalid showtime status. Must be one of: ${enumEntries<ShowtimeStatus>().joinToString { it.name }}"
        validate<ShowtimeDTO> { request ->
            val isShowtimeStatusValid = enumEntries<ShowtimeStatus>().any { it.name == request.status.uppercase() }
            when {
                request.movieId <= 0 -> invalidResponseResult("Invalid movie id")
                request.roomId <= 0 -> invalidResponseResult("Invalid room id")
                request.startsAt == null || request.endsAt == null -> invalidResponseResult("Invalid start at or end at")
//                request.startsAt >= request.endsAt -> invalidResponseResult("Start time can't be greater than end time")
                request.price <= 0 -> invalidResponseResult("Invalid price")
                !isShowtimeStatusValid -> invalidResponseResult(showtimeStatusErrorMessage)
                else -> ValidationResult.Valid
            }
        }

        validate<UpdateShowtimeStatusRequest> { request ->
            val isShowtimeStatusValid = enumEntries<ShowtimeStatus>().any { it.name == request.status.uppercase() }
            if (isShowtimeStatusValid.not()) {
                invalidResponseResult(showtimeStatusErrorMessage)
            } else ValidationResult.Valid
        }

        validate<CreateReservationRequest> { request ->
            when {
                request.showtimeId <= 0 -> invalidResponseResult("Invalid showtime id")
                request.seatIds.isEmpty() -> invalidResponseResult("Seat(s) is required")
                request.seatIds.size != request.seatIds.distinct().size -> invalidResponseResult("Duplicate seats in request")
                else -> ValidationResult.Valid
            }
        }
    }
}

private fun invalidResponseResult(message: String) = ValidationResult.Invalid(message)
