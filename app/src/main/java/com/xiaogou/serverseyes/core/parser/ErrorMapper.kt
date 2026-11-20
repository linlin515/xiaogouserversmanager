package com.xiaogou.serverseyes.core.parser

import com.xiaogou.serverseyes.core.model.UserFacingError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HostKeyUntrustedException(
    val fingerprint: String,
    val keyType: String
) : Exception("Untrusted SSH host key: $fingerprint ($keyType)")

class HostKeyMismatchException(
    val expected: String,
    val actual: String
) : Exception("SSH host key changed: expected=$expected actual=$actual")

class AgentAuthException(val code: Int) : Exception("Agent HTTP auth error: $code")

object ErrorMapper {
    fun toUserFacingError(throwable: Throwable): UserFacingError {
        return when (throwable) {
            is HostKeyUntrustedException -> UserFacingError.HostKeyUntrusted(
                fingerprint = throwable.fingerprint,
                keyType = throwable.keyType
            )
            is HostKeyMismatchException -> UserFacingError.HostKeyChanged(
                expected = throwable.expected,
                actual = throwable.actual
            )
            is AgentAuthException -> UserFacingError.AgentUnauthorized()
            is SocketTimeoutException, is ConnectException, is UnknownHostException ->
                UserFacingError.NetworkUnreachable()
            else -> {
                val msg = throwable.message.orEmpty().lowercase()
                when {
                    "auth fail" in msg || "userauth" in msg || "invalid privatekey" in msg ->
                        UserFacingError.AuthFailed()
                    "timeout" in msg || "refused" in msg || "unreachable" in msg || "reset" in msg ->
                        UserFacingError.NetworkUnreachable()
                    else -> UserFacingError.Unknown("无法完成采集，请检查网络或凭据配置")
                }
            }
        }
    }
}
