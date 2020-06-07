package com.arbiter34.ovpnconnect

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "scanQrCode")
class ScanQRCode(
): Runnable {
    @Option(names = ["-p", "--password"], description = ["Password"], interactive = true)
    private var password: CharArray = CharArray(0)

    @Option(names = ["-u", "--username"], description = ["Your OpenVPN username"], required = true)
    private var username: String = ""

    override fun run() {
        TODO("Not yet implemented")
    }
}