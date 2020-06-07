package com.arbiter34.ovpnconnect

import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Command(subcommands = [OpenVPNConnect::class, ScanQRCode::class])
class Utility: Callable<Int> {
    override fun call(): Int {
        CommandLine(OpenVPNConnect()).execute(*listOf<String>().toTypedArray())
        CommandLine(ScanQRCode()).execute(*listOf<String>().toTypedArray())
        return -1
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(Utility()).execute(*args)
    System.exit(exitCode)
}