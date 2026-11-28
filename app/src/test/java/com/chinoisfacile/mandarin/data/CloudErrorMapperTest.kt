package com.chinoisfacile.mandarin.data

import com.chinoisfacile.mandarin.data.cloud.CloudErrorMapper
import com.chinoisfacile.mandarin.data.cloud.UnsupportedBackupSchemaException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudErrorMapperTest {

    @Test
    fun toUserMessage_mapsNetworkErrors() {
        val hostMsg = CloudErrorMapper.toUserMessage(UnknownHostException("example.supabase.co"))
        val wrappedTimeoutMsg = CloudErrorMapper.toUserMessage(
            IllegalStateException("Request failed", SocketTimeoutException("timeout")),
        )

        assertTrue(hostMsg.contains("No internet connection"))
        assertEquals(hostMsg, wrappedTimeoutMsg)
    }

    @Test
    fun toUserMessage_mapsUnsupportedSchemaException() {
        val msg = CloudErrorMapper.toUserMessage(UnsupportedBackupSchemaException(remoteVersion = 2))
        assertTrue(msg.contains("newer version"))
    }

    @Test
    fun mapAuthError_mapsCommonSupabaseAuthCodes() {
        assertEquals(
            "Invalid email or password.",
            CloudErrorMapper.mapAuthError("invalid_credentials", null),
        )
        assertEquals(
            "Please confirm your email address before signing in.",
            CloudErrorMapper.mapAuthError("email_not_confirmed", null),
        )
        assertEquals(
            "An account with this email already exists. Try signing in instead.",
            CloudErrorMapper.mapAuthError("user_already_exists", null),
        )
        assertEquals(
            "Password must be at least 6 characters.",
            CloudErrorMapper.mapAuthError("weak_password", null),
        )
        assertEquals(
            "Too many attempts. Please wait a moment and try again.",
            CloudErrorMapper.mapAuthError("over_request_rate_limit", null),
        )
    }
}
