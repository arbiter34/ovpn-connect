package com.arbiter34.ovpnconnect.grpc.observers

import com.arbiter34.ovpnconnect.OpenVPN
import com.arbiter34.ovpnconnect.grpc.OpenVpnHolder
import com.arbiter34.ovpnconnect.proto.Connect.Action.CONNECT
import com.arbiter34.ovpnconnect.proto.Connect.Action.DISCONNECT
import com.arbiter34.ovpnconnect.proto.Connect.Action.STATUS
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionRequest
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionResponse
import com.arbiter34.ovpnconnect.util.AES
import com.arbiter34.ovpnconnect.util.logger
import io.grpc.stub.StreamObserver
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class ConnectionRequestObserver(
    private val responder: StreamObserver<ConnectionResponse>,
    private val sessions: ConcurrentHashMap<String, OpenVpnHolder>
): StreamObserver<ConnectionRequest> {

    private var request: ConnectionRequest? = null
    /**
     * Receives a value from the stream.
     *
     *
     * Can be called many times but is never called after [.onError] or [ ][.onCompleted] are called.
     *
     *
     * Unary calls must invoke onNext at most once.  Clients may invoke onNext at most once for
     * server streaming calls, but may receive many onNext callbacks.  Servers may invoke onNext at
     * most once for client streaming calls, but may receive many onNext callbacks.
     *
     *
     * If an exception is thrown by an implementation the caller is expected to terminate the
     * stream by calling [.onError] with the caught exception prior to
     * propagating it.
     *
     * @param value the value passed to the stream
     */
    override fun onNext(value: ConnectionRequest?) {
        if (value == null) {
            return
        }
        val existing = sessions[value.toHash()]
        if (value.action == CONNECT && existing?.openVpn?.process?.isAlive == true) {
            throw IllegalStateException("Looks like you've already connected to the VPN!")
        }

        if (value.action == DISCONNECT) {
            val session = sessions[value.toHash()]
                ?: throw InternalError("Unable to find existing session to disconnect")
            session.openVpn.kill()
            sessions.remove(value.toHash())
        }

        if (value.action == CONNECT) {
            val file = File(value.encryptedSecretPath)
            if (!file.exists()) {
                throw IllegalArgumentException("Unable to find file ${file.absolutePath}")
            }

            val secret = FileInputStream(file)
                .use {
                    AES.decrypt(
                        value.password.toByteArray(Charsets.UTF_8),
                        value.username.toByteArray(Charsets.UTF_8),
                        it.readAllBytes()
                    )
                }
            sessions[value.toHash()] = OpenVpnHolder(
                this,
                OpenVPN(
                    value.username,
                    value.password,
                    secret,
                    value.configPath,
                    deadCallback = {
                        logger().error("Disconnected from OpenVPN on client with PID $it")
                    }
                )
            )
        }

        if (value.action == STATUS)  {
            val session = sessions[value.toHash()]
            if (session == null) {
                responder.onError(IllegalStateException("No active session for ${value.username}"))
                return
            }
            responder.onNext(
                ConnectionResponse.newBuilder()
                    .setLog(session.openVpn.popLog())
                    .setStatus(session.openVpn.status.get())
                    .build()
            )
        }
    }

    /**
     * Receives a terminating error from the stream.
     *
     *
     * May only be called once and if called it must be the last method called. In particular if an
     * exception is thrown by an implementation of `onError` no further calls to any method are
     * allowed.
     *
     *
     * `t` should be a [io.grpc.StatusException] or [ ], but other `Throwable` types are possible. Callers should
     * generally convert from a [io.grpc.Status] via [io.grpc.Status.asException] or
     * [io.grpc.Status.asRuntimeException]. Implementations should generally convert to a
     * `Status` via [io.grpc.Status.fromThrowable].
     *
     * @param t the error occurred on the stream
     */
    override fun onError(t: Throwable?) {
        responder.onError(t)
        logger().error("Error during connection: ", t)
    }

    /**
     * Receives a notification of successful stream completion.
     *
     *
     * May only be called once and if called it must be the last method called. In particular if an
     * exception is thrown by an implementation of `onCompleted` no further calls to any method
     * are allowed.
     */
    override fun onCompleted() {
        logger().info("Socket was closed for ${request?.username ?: "Unauthenticated user"}")
    }

    fun ConnectionRequest.toHash(): String {
        return MessageDigest.getInstance("SHA512")
            .digest(
                "$username$password$configPath$encryptedSecretPath".toByteArray(Charsets.UTF_8)
            ).toString(Charsets.UTF_8)
    }
}