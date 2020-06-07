package com.arbiter34.ovpnconnect.util

import com.grunka.random.fortuna.Fortuna
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AES {

    fun encrypt(
        secret: ByteArray,
        salt: ByteArray,
        payload: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(secret.toString(Charsets.UTF_8).toCharArray(), salt, 500, 32 * 8)
        val temp = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
        val secretKey = SecretKeySpec(temp.encoded, "AES")

        val fortuna = Fortuna.createInstance()

        val iv = ByteArray(12)
        fortuna.nextBytes(iv)
        fortuna.shutdown()

        val gcmParameterSpec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
        val encryptedPayload = cipher.doFinal(payload)

        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + Int.SIZE_BYTES + iv.size + encryptedPayload.size)
        buffer.putInt(iv.size)
        buffer.putInt(encryptedPayload.size)
        buffer.put(iv)
        buffer.put(encryptedPayload)
        return buffer.array()
    }

    fun decrypt(
        secret: ByteArray,
        salt: ByteArray,
        payload: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(secret.toString(Charsets.UTF_8).toCharArray(), salt, 500, 32 * 8)
        val temp = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
        val secretKey = SecretKeySpec(temp.encoded, "AES")

        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.int
        val encryptedPayloadSize = buffer.int
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val encryptedPayload = ByteArray(encryptedPayloadSize)
        buffer.get(encryptedPayload)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmParamSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParamSpec)
        return cipher.doFinal(encryptedPayload)
    }
}