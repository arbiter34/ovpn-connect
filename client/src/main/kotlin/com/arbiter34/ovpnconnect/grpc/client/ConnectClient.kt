package com.arbiter34.ovpnconnect.grpc.client

import com.arbiter34.ovpnconnect.proto.ConnectionServiceGrpc
import com.arbiter34.ovpnconnect.proto.ConnectionServiceGrpc.ConnectionServiceFutureStub
import com.arbiter34.ovpnconnect.proto.ConnectionServiceGrpc.ConnectionServiceStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class ConnectClient(
    private val channelBuilder: ManagedChannelBuilder<*>
) {
    val channel = channelBuilder.build()
    val client = ConnectionServiceGrpc.newStub(channel)

    fun connect() {
        client.connection()
    }

}