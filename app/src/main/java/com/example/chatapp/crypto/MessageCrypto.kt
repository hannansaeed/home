package com.example.chatapp.crypto

import javax.inject.Inject
import javax.inject.Singleton

/**
 * E2EE boundary. Everything leaving the device goes through encrypt(),
 * everything arriving goes through decrypt(). Swap the pass-through
 * implementation for libsignal (Signal protocol) before shipping.
 */
interface MessageCrypto {
    suspend fun encrypt(recipientId: String, plaintext: ByteArray): ByteArray
    suspend fun decrypt(senderId: String, ciphertext: ByteArray): ByteArray
}

@Singleton
class PassThroughCrypto @Inject constructor() : MessageCrypto {
    // TODO: replace with libsignal session ciphers, keys in Android Keystore
    override suspend fun encrypt(recipientId: String, plaintext: ByteArray) = plaintext
    override suspend fun decrypt(senderId: String, ciphertext: ByteArray) = ciphertext
}
