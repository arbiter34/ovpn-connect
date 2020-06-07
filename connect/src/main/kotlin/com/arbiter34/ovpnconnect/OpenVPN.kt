package com.arbiter34.ovpnconnect

import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.AUTH_REQUESTED
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.AUTH_REQUESTED_VALUE
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.CHALLENGE_RESPONSE_WAIT
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.CONNECTED
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.ERROR
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.PASSWORD_SENT
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.STARTED
import com.arbiter34.ovpnconnect.proto.Connect.ConnectionStatus.TOTP_SENT
import com.warrenstrange.googleauth.GoogleAuthenticator
import org.apache.commons.codec.binary.Base32
import org.apache.commons.net.telnet.TelnetClient
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class OpenVPN(
    private val username: String,
    private val password: String,
    private val secret: ByteArray,
    private val configPath: String,
    private val managementPort: Int = 7777,
    private val deadCallback: (Long) -> Unit = {}
) {
    private val done = AtomicBoolean(false)
    private val pid: AtomicLong
    private val logStack = ConcurrentLinkedDeque<String>()
    val process: Process
    var status = AtomicReference(STARTED)


    init {
        Runtime.getRuntime()
            .addShutdownHook(
                thread(start = false) {
                    done.set(true)
                }
            )
        process = Runtime.getRuntime().exec("openvpn --config $configPath --management 127.0.0.1 $managementPort --management-hold --management-forget-disconnect --management-query-passwords")
        pid = AtomicLong(process.pid())
    }

    fun popLog(): String {
        return logStack.pop()
    }

    fun kill() {
        process.destroy()
        done.set(true)
    }

    fun connect() {
        try {

            if (!process.isAlive) {
                val input = process.inputStream.readAllBytes().toString(Charsets.US_ASCII)
                val error = process.errorStream.readAllBytes().toString(Charsets.US_ASCII)
                status.set(ERROR)
                println("OH SHIT: $input")
                println("ERROR: $error")
                return
            }
            val done = AtomicBoolean(false)
            val systemError = AtomicBoolean(false)
            val sendHoldRelease = AtomicBoolean(false)
            val sendUserName = AtomicBoolean(false)
            val sendPassword = AtomicBoolean(false)
            val startTOTP = AtomicReference<List<String>>()
            val finishTOTP = AtomicBoolean(false)
            val authToken = AtomicReference<String>("")

            val client = TelnetClient()

            client.connect("127.0.0.1",  7777)

            val inputStream = client.inputStream
            thread {
                inputStream.use {
                    val bytes = mutableListOf<Byte>()
                    while (true) {
                        if (systemError.get() || done.get()) {
                            break
                        }
                        val res = it.read()
                        if (res == -1) {
                            break
                        }
                        bytes.add(res.toByte())
                        if (bytes.size > 3 && bytes[bytes.size-2].toChar() == '\r' && res.toChar() == '\n') {
                            val line = bytes.toByteArray().toString(Charsets.US_ASCII)
                            logStack.add(line)
                            println(line)
                            when {
                                line.contains("HOLD") && !sendHoldRelease.get() -> sendHoldRelease.set(true)
                                line.startsWith(">PASSWORD") && !sendUserName.get() -> println("Authentication is beginning.")
                                    .also {
                                        sendUserName.set(true)
                                        status.set(AUTH_REQUESTED)
                                    }
                                line.contains("SUCCESS: 'Auth' username entered") -> println("Username sent, sending pw.")
                                    .also {
                                        sendPassword.set(true)
                                        status.set(PASSWORD_SENT)
                                    }
                                line.contains("SUCCESS: 'Auth' password entered") -> println("pw sent")
                                line.contains("ERROR") -> systemError.set(true)
                                line.contains(">PASSWORD:Verification Failed: 'Auth'") -> line.replace("^.*?\\['([^'])".toRegex(), "$1")
                                    .split(":")
                                    .let { startTOTP.set(it) }
                                    .also {
                                        status.set(CHALLENGE_RESPONSE_WAIT)
                                    }
                                line.contains(">PASSWORD:Need 'Auth' username/password") && startTOTP.get().isNotEmpty() -> {
                                    finishTOTP.set(true)
                                    status.set(TOTP_SENT)
                                }
                                line.contains(">PASSWORD:Auth-Token") -> {
                                    authToken.set(line.replace(">PASSWORD:Auth-Token:", ""))
                                    status.set(CONNECTED)
                                }
                            }
                            bytes.clear()
                        }
                    }
                }
            }

            val outputStream = client.outputStream
            thread {
                var holdReleaseSent = false
                var usernameSent = false
                var passowrdSent = false
                var totpSent = false
                outputStream.use {
                    val writer = outputStream.writer(Charsets.US_ASCII)
                    while(true) {
                        if (systemError.get() || done.get()) {
                            break
                        }
                        when {
                            sendHoldRelease.get() && !holdReleaseSent -> {
                                holdReleaseSent = true
                                Thread.sleep(500)
                                writer.write("auth-retry interact\r\n")
                                writer.write("hold release\r\n")
                                writer.write("hold off\r\n")
                                writer.flush()
                            }
                            sendUserName.get() && !usernameSent -> {
                                usernameSent = true
                                Thread.sleep(500)
                                writer.write("username 'Auth' ${username}\n\n")
                                writer.flush()
                            }
                            sendPassword.get() && !passowrdSent -> {
                                passowrdSent = true
                                Thread.sleep(500)
                                writer.write("password 'Auth' ${password}\r\n")
                                writer.flush()
                            }
                            startTOTP.get()?.isNotEmpty() == true && !totpSent -> {
                                totpSent = true
                                val arr = startTOTP.get()
                                while (!finishTOTP.get()) {}
                                val totp = GoogleAuthenticator()
                                    .getTotpPassword(
                                        Base32()
                                            .encodeAsString(
                                                secret
                                            )
                                    )
                                println("TOTP: $totp")
                                writer.write("username 'Auth' ${String(Base64.getDecoder().decode(arr[3]))}\r\n")
                                writer.flush()
                                writer.write("password 'Auth' ${arr[0]}::${arr[2]}::$totp\r\n")
                                writer.flush()
                            }
                        }
                    }
                }
            }

            while (!systemError.get() && !done.get()) {
                Thread.sleep(1000)
            }
            if (systemError.get()) {
                deadCallback(pid.get())
            }
        } catch (t: Throwable) {
            if (process.isAlive) {
                process.destroy()
            }
        }
    }
}