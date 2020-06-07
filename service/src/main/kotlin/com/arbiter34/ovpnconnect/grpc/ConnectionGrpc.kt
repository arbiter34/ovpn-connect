package com.arbiter34.ovpnconnect.grpc
import com.arbiter34.ovpnconnect.OpenVPN
import com.arbiter34.ovpnconnect.grpc.observers.ConnectionRequestObserver
import com.arbiter34.ovpnconnect.proto.Connect.Action.CONNECT
import com.arbiter34.ovpnconnect.proto.Connect.Action.DISCONNECT
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionRequest
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionResponse
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.DISCONNECTED
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.ERROR
import com.arbiter34.ovpnconnect.proto.ConnectionServiceGrpc.ConnectionServiceImplBase
import com.arbiter34.ovpnconnect.util.AES
import com.arbiter34.ovpnconnect.util.logger
import io.grpc.stub.StreamObserver
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class OpenVpnHolder(
    val observer: ConnectionRequestObserver,
    val openVpn: OpenVPN
)

class ConnectionGrpc: ConnectionServiceImplBase() {
    companion object {
        private val sessions = ConcurrentHashMap<String, OpenVpnHolder>()
    }

    override fun connection(
        request: ConnectionRequest,
        responseObserver: StreamObserver<ConnectionResponse>
    ) {
        when (request.action) {
            CONNECT -> connect(request)
            DISCONNECT -> disconnect(request)
        }


    }

    private fun connect(
        request: ConnectionRequest
    ) {
        val file = File(request.encryptedSecretPath)
        if (!file.exists()) {
            throw IllegalArgumentException("File ${request.encryptedSecretPath} does not exist.")
        }

        val existing = sessions[request.toHash()]
        if (existing != null && existing.openVpn.process.isAlive) {
            throw IllegalStateException("Unable to open more than one vpn connection per user.")
        }

        val secret = AES.decrypt(
            request.password.toByteArray(Charsets.UTF_8),
            request.username.toByteArray(Charsets.UTF_8),
            FileInputStream(file).use { it.readAllBytes() }
        )

        OpenVPN(
            request.username,
            request.password,
            secret,
            request.configPath,
            deadCallback = { x -> x.also { logger().error("Disconnected from VPN with code $x.") } }
        ).connect()
    }

    private fun disconnect(
        request: ConnectionRequest
    ): ConnectionStatus {
        val session = sessions[request.toHash()]

        if (session != null && session.openVpn.process.isAlive) {
            session.openVpn.kill()
            return DISCONNECTED
        }
        return ERROR
    }
}

fun ConnectionRequest.toHash(): String {
    return Base64.getEncoder().encodeToString("$username:$password:$configPath".toByteArray(Charsets.UTF_8))
}
