package com.martdev.flickq.features.auth.api

import com.martdev.flickq.auth.request.CreateUserRequest
import com.martdev.flickq.auth.request.RefreshTokenRequest
import com.martdev.flickq.auth.request.ResendOTPRequest
import com.martdev.flickq.auth.request.UserLoginRequest
import com.martdev.flickq.auth.request.UserVerificationRequest
import com.martdev.flickq.config.CookieConfig
import com.martdev.flickq.features.auth.domain.service.UserService
import com.martdev.flickq.shared.DataResponse
import com.martdev.flickq.shared.domain.exception.UnauthorizedException
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val service by inject<UserService>()
    val cookieConfig by inject<CookieConfig>()
    route("/authentication") {
        /**
         * Tag: authentication
         *
         * Registers a user
         *
         * Responses:
         *      - 201 [com.martdev.features.auth.api.response.CreateUserResponse]
         *      - 400 [com.martdev.shared.api.ErrorResponse] duplicate email
         *      - 500 [com.martdev.shared.api.ErrorResponse]
         */
        post("/register") {
            val userRequest = call.receive<CreateUserRequest>()
            val response = service.registerUser(userRequest.toCredentials()).toCreateUserResponse()
            val dataResponse = DataResponse(response)
            call.respond(status = HttpStatusCode.Created, dataResponse)
        }

        /**
         * Tag: authentication
         *
         * Verifies a user
         *
         * Responses:
         *      - 200 the user is verified
         *      - 400 [com.martdev.shared.api.ErrorResponse] Invalid or expired OTP
         *      - 404 [com.martdev.shared.api.ErrorResponse] Invalid or expired verification token
         *      - 500 [com.martdev.shared.api.ErrorResponse] An error occurred during verification
         */
        post("/verify-user") {
            val request = call.receive<UserVerificationRequest>()
            service.verifyUser(request.toVerificationInput())
            call.respond(HttpStatusCode.OK)
        }

        /**
         * Tag: authentication
         *
         * Login a user
         *
         * Responses:
         *      - 200 [com.martdev.features.auth.api.response.UserLoginResponse]
         *      - 400 [com.martdev.shared.api.ErrorResponse] Invalid email or password
         *      - 404 [com.martdev.shared.api.ErrorResponse]
         *      - 429 [com.martdev.shared.api.ErrorResponse] too many requests
         *      - 500 [com.martdev.shared.api.ErrorResponse]
         */
        rateLimit(RateLimitName("login")) {
            post("/login") {
                val request = call.receive<UserLoginRequest>().toCredentials()
                application.environment.log.info("From server $request")
                val response = service.loginUser(request).toUserLoginResponse()
                // Native clients read the refresh token from the body; web ignores it and relies on
                // this httpOnly cookie instead (XSS-safe — JS can't read it).
                call.setRefreshTokenCookie(response.refreshToken, cookieConfig)
                val dataResponse = DataResponse(response)
                call.respond(status = HttpStatusCode.OK, dataResponse)
            }
        }

        rateLimit(RateLimitName("login")) {
            post("/admin/login") {
                val request = call.receive<UserLoginRequest>().toCredentials()
                val response = service.loginUser(request, true).toUserLoginResponse()
                // Native clients read the refresh token from the body; web ignores it and relies on
                // this httpOnly cookie instead (XSS-safe — JS can't read it).
                call.setRefreshTokenCookie(response.refreshToken, cookieConfig)
                val dataResponse = DataResponse(response)
                call.respond(status = HttpStatusCode.OK, dataResponse)
            }
        }

        /**
         * Tag: authentication
         *
         * Refresh access token
         *
         * Responses:
         *      - 200 [com.martdev.features.auth.api.response.RefreshTokenResponse]
         *      - 401 [com.martdev.shared.api.ErrorResponse] unauthorized
         *      - 500 [com.martdev.shared.api.ErrorResponse]
         */
        post("/refresh-token") {
            // Native sends the token in the body (request validation still applies → 400 on blank);
            // web sends no body and the browser attaches the httpOnly cookie. Gate on body presence
            // so we don't swallow validation errors. 401 when neither body nor cookie carries a token.
            val token = if ((call.request.contentLength() ?: 0L) > 0L) {
                call.receive<RefreshTokenRequest>().refreshToken
            } else {
                call.readRefreshTokenCookie() ?: throw UnauthorizedException()
            }
            val response = service.refreshToken(token).toRefreshTokenResponse()
            call.setRefreshTokenCookie(response.refreshToken, cookieConfig)
            val dataResponse = DataResponse(response)
            call.respond(status = HttpStatusCode.OK, dataResponse)
        }

        /**
         * Tag: authentication
         *
         * Logs out — revokes the current refresh token and clears the cookie. Idempotent: an
         * unknown/already-revoked token (or none at all) still returns 200, and the cookie is
         * cleared either way.
         *
         * Responses:
         *      - 200 logged out
         */
        post("/logout") {
            // Web logout carries only the cookie; native sends the token in the body. Prefer the
            // cookie, tolerate a bad/absent body — logout must not fail.
            val token = call.readRefreshTokenCookie()
                ?: if ((call.request.contentLength() ?: 0L) > 0L) {
                    runCatching { call.receive<RefreshTokenRequest>().refreshToken }.getOrNull()
                } else {
                    null
                }
            if (!token.isNullOrBlank()) service.logout(token)
            call.clearRefreshTokenCookie(cookieConfig)
            call.respond(HttpStatusCode.OK)
        }

        /**
         * Tag: authentication
         *
         * Resend verification code
         *
         * Responses:
         *      - 200 [com.martdev.features.auth.api.response.ResendOTPResponse]
         *      - 400 [com.martdev.shared.api.ErrorResponse] User is already verified
         *      - 500 [com.martdev.shared.api.ErrorResponse] Failed to resend OTP
         */
        rateLimit(RateLimitName("resend-otp")) {
            post("/resend-otp") {
                val request = call.receive<ResendOTPRequest>()
                val response = service.resendOTP(request.email).toResendOTPResponse()
                val dataResponse = DataResponse(response)
                call.respond(status = HttpStatusCode.OK, dataResponse)
            }
        }
    }
}
