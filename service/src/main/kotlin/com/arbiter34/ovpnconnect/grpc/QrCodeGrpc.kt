package com.arbiter34.ovpnconnect.grpc

import com.arbiter34.ovpnconnect.proto.QrCode.DecodeQrRequest
import com.arbiter34.ovpnconnect.proto.QrCode.DecodeQrResponse
import com.arbiter34.ovpnconnect.proto.QrDecodeServiceGrpc.QrDecodeServiceImplBase
import com.arbiter34.ovpnconnect.qr.QRDecoder
import com.arbiter34.ovpnconnect.util.AES
import io.grpc.stub.StreamObserver
import java.io.FileOutputStream
import com.arbiter34.ovpnconnect.proto.QrCode.Result.RAW_BYTES_RETURNED
import com.google.protobuf.ByteString
import java.io.File

class QrCodeGrpc: QrDecodeServiceImplBase() {
    /**
     */
    override fun decodeQr(
        request: DecodeQrRequest,
        responseObserver: StreamObserver<DecodeQrResponse>
    ) {
        val qrSecret = QRDecoder.decode(request.imageBytes.toByteArray())

        val encryptedSecret = AES.encrypt(
            request.ovpnPassword.toByteArray(Charsets.UTF_8),
            request.ovpnUsername.toByteArray(Charsets.UTF_8),
            qrSecret
        )

        when {
            request.encryptAndStoreLocally -> {
                FileOutputStream(File(request.localPath))
                    .use {
                        it.write(
                            encryptedSecret
                        )
                    }
            }
            else -> responseObserver.onNext(
                DecodeQrResponse.newBuilder()
                    .setResult(RAW_BYTES_RETURNED)
                    .setSecret(ByteString.copyFrom(qrSecret))
                    .build()
            )
        }

    }
}