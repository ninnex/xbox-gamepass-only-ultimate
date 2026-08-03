package com.ninnex.xboxgamepass

import java.security.SecureRandom
import java.util.Base64

internal object MicrosoftCorrelationVector {
    private const val ROOT_BYTE_COUNT = 16
    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder().withoutPadding()

    fun generate(): String {
        val root = ByteArray(ROOT_BYTE_COUNT)
        secureRandom.nextBytes(root)
        return "${encoder.encodeToString(root)}.0"
    }
}
