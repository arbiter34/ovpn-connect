package com.arbiter34.ovpnconnect

import com.arbiter34.ovpnconnect.qr.QRDecoder
import com.arbiter34.ovpnconnect.util.AES
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable

@Command(name = "scanQrCode")
class ScanQRCode(
): Callable<Int> {
    @Option(names = ["-p", "--password"], description = ["Password"], interactive = true)
    private var password: CharArray = CharArray(0)

    @Option(names = ["-u", "--username"], description = ["Your OpenVPN username"], required = true)
    private var username: String = ""

    @Option(names = ["-f", "--file"], description = ["File path to the QR Code Image"])
    private var qrPath: String = ""

    @Option(names = ["-o", "--output"], description = ["File path to output your encrypted secret to be used with the connect command."])
    private var outputPath: String = ""

    override fun call(): Int {
        val secret = QRDecoder.decode(qrPath)

        val encrypted = AES.encrypt(
            password.map { "$it" }.reduce { acc, s -> "$acc$s" }.toByteArray(Charsets.UTF_8),
            username.toByteArray(Charsets.UTF_8),
            secret
        )

        FileOutputStream(File(outputPath))
            .use {
                it.write(encrypted)
            }
        return 0
    }
}