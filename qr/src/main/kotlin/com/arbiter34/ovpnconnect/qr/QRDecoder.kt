package com.arbiter34.ovpnconnect.qr

import com.arbiter34.ovpn.TOTP.MigrationPayload
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import javax.imageio.ImageIO

object QRDecoder {

    fun decode(
        filePath: String
    ): ByteArray {
        val file = File(filePath)

        if (!file.exists()) {
            throw IllegalStateException("Unable to find file ${file.absolutePath}")
        }

        val image = ImageIO.read(file)
        val luminanceSource = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(luminanceSource))

        val text = MultiFormatReader().decode(bitmap).text
        val uri = URI(text)


        val queryMap = mutableMapOf<String, String>()
        val queries = uri.rawQuery.split("=")
        for (i in 0 until queries.size / 2) {
            queryMap[queries[i]] = URLDecoder.decode(queries[i+1])
        }
        return MigrationPayload.parseFrom(
            Base64.getDecoder()
                .decode(
                    queryMap["data"]
                )
        ).otpParametersList
            .first()
            .secret
            .toByteArray()
    }
}