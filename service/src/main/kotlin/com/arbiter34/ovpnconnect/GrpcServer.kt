package com.arbiter34.ovpnconnect

import com.arbiter34.ovpnconnect.grpc.ConnectionGrpc
import com.arbiter34.ovpnconnect.grpc.QrCodeGrpc
import com.arbiter34.ovpnconnect.util.logger
import io.grpc.ServerBuilder

class GrpcServer(
    private val port: Int
) {
    private val server = ServerBuilder
        .forPort(port)
        .addService(QrCodeGrpc())
        .addService(ConnectionGrpc())
        .build()


    fun start() {
        server.start()
        logger().info("GRPC Server started at $port")
        server.awaitTermination()
    }
}

fun main(args: Array<String>) {
    GrpcServer(8080).start()
}
