package com.chinoisfacile.mandarin.data.cloud

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

object CloudErrorMapper {

    fun toUserMessage(throwable: Throwable): String {
        val schemaError = findCause<UnsupportedBackupSchemaException>(throwable)
        if (schemaError != null) {
            return "This backup was created with a newer version of EasyMandarin. Please update the app first."
        }

        if (isNetworkError(throwable)) {
            return "No internet connection. Please check your network and try again."
        }

        if (findCause<AuthWeakPasswordException>(throwable) != null) {
            return "Password must be at least 6 characters."
        }

        val authRest = findCause<AuthRestException>(throwable)
        if (authRest != null) {
            val code = authRest.errorCode?.value ?: authRest.errorCode?.name.orEmpty()
            return mapAuthError(code = code, rawMessage = authRest.message)
        }

        return mapGenericError(throwable.message.orEmpty())
    }

    internal fun mapAuthError(code: String, rawMessage: String?): String {
        val normalized = "${code.lowercase()} ${rawMessage.orEmpty().lowercase()}"
        return when {
            "invalid_credentials" in normalized ||
                "invalid login credentials" in normalized ||
                "invalid_grant" in normalized ->
                "Invalid email or password."

            "email_not_confirmed" in normalized ||
                "email not confirmed" in normalized ->
                "Please confirm your email address before signing in."

            "user_already_exists" in normalized ||
                "email_exists" in normalized ||
                "already registered" in normalized ->
                "An account with this email already exists. Try signing in instead."

            "weak_password" in normalized ||
                "password should be at least" in normalized ->
                "Password must be at least 6 characters."

            "over_request_rate_limit" in normalized ||
                "over_email_send_rate_limit" in normalized ||
                "rate limit" in normalized ||
                "429" in normalized ->
                "Too many attempts. Please wait a moment and try again."

            "validation_failed" in normalized ||
                "email_address_invalid" in normalized ||
                "unable to validate email" in normalized ->
                "Please enter a valid email address."

            else -> "Authentication failed. Please check your details and try again."
        }
    }

    internal fun mapGenericError(rawMessage: String): String {
        val lower = rawMessage.lowercase()
        return when {
            "invalid login credentials" in lower || "invalid_credentials" in lower ->
                "Invalid email or password."

            "email not confirmed" in lower || "email_not_confirmed" in lower ->
                "Please confirm your email address before signing in."

            "already registered" in lower || "user_already_exists" in lower ->
                "An account with this email already exists. Try signing in instead."

            "rate limit" in lower || "429" in lower ->
                "Too many attempts. Please wait a moment and try again."

            else -> "Cloud sync failed. Please try again later."
        }
    }

    fun isNetworkError(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (
                current is UnknownHostException ||
                current is ConnectException ||
                current is SocketTimeoutException ||
                current is UnresolvedAddressException ||
                current is HttpRequestTimeoutException ||
                current is IOException
            ) {
                return true
            }
            val msg = current.message?.lowercase().orEmpty()
            if (
                "unable to resolve host" in msg ||
                "failed to connect" in msg ||
                "network is unreachable" in msg ||
                "timeout" in msg
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private inline fun <reified T : Throwable> findCause(throwable: Throwable): T? {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is T) return current
            current = current.cause
            depth++
        }
        return null
    }

    private const val MAX_CAUSE_DEPTH = 10
}
