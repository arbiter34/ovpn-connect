package com.arbiter34.ovpnconnect.grpc.client

import com.arbiter34.ovpnconnect.proto.Connect.Action
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionRequest
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

    fun connect(
        username: String,
        password: String,
        configFile: String? = null,
        config: String? = null
    ) {
        TODO("")
//        if (config == null && configFile == null) {
//
//        }
//        client.connection(
//            ConnectionRequest.newBuilder()
//                .setAction(Action.CONNECT)
//
//        )
    }

}