package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AdminSession(
    val sessionToken: String,
    val adminEmail: String,
    val sessionVersion: Int,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun isValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        sessionToken.isNotBlank() && nowMillis < expiresAtMillis
}

object AdminSecurity {
    const val DEFAULT_ITERATIONS = 100_000
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L
    const val SESSION_DURATION_MS = 60 * 60 * 1000L

    private val secureRandom = SecureRandom()

    fun generateSaltHex(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString()
    }

    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString()
    }

    fun hashPasswordPbkdf2(
        password: String,
        saltHex: String,
        iterations: Int = DEFAULT_ITERATIONS
    ): String {
        val salt = saltHex.hexToBytes()
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded.toHexString()
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPassword(
        password: String,
        saltHex: String,
        expectedHashHex: String,
        iterations: Int = DEFAULT_ITERATIONS
    ): Boolean {
        if (password.isEmpty() || saltHex.isEmpty() || expectedHashHex.isEmpty()) return false
        val actualHash = hashPasswordPbkdf2(password, saltHex, iterations)
        return MessageDigest.isEqual(
            actualHash.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        require(len % 2 == 0) { "Invalid hex string length" }
        return ByteArray(len / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
