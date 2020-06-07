package com.arbiter34.ovpnconnect

import com.arbiter34.ovpnconnect.util.AES
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Callable

@Command(name = "connect")
class OpenVPNConnect: Callable<Int> {
    
    @Option(names = ["-f", "--file" ], description = ["Path to your ovpn config file."], required = true)
    private var configPath: String = ""

    @Option(names = ["-u", "--username"], description = ["Your OpenVPN username"], required = true)
    private var username: String = ""

    @Option(names = ["-p", "--password"], description = ["Password"], interactive = true, required = true)
    private var password: CharArray = CharArray(0)

    @Option(names = ["-s", "--qr-secret-file"], description = ["Location to encrypted secret file output from ScanQRCode command."], required = true)
    private var secretFile: String = ""

    override fun call(): Int {
        val passwordString = password.map { "$it" }.reduce { acc, c -> "$acc$c" }
        val secret = FileInputStream(File(secretFile))
            .use {
                AES.decrypt(
                    passwordString.toByteArray(Charsets.UTF_8),
                    username.toByteArray(Charsets.UTF_8),
                    it.readAllBytes()
                )
            }

        OpenVPN(
            username,
            passwordString,
            secret,
            configPath
        ).connect()
        return 0
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            CommandLine(OpenVPNConnect()).execute(*args)
        }
    }
}