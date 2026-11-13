package com.example.chatapp.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E2EE boundary. Everything leaving the device goes through encrypt(),
 * everything arriving goes through decrypt().
 */
interface MessageCrypto {
    suspend fun encrypt(recipientId: String, plaintext: ByteArray): ByteArray
    suspend fun decrypt(senderId: String, ciphertext: ByteArray): ByteArray
}

/**
 * Real End-to-End Encryption using ECDH (P-256 / secp256r1) + HKDF-SHA256 + AES-256-GCM.
 * Each user's EC private key is sealed at rest via an Android Keystore AES-256-GCM master key.
 */
@Singleton
class EcdhAesGcmCrypto @Inject constructor(
    @ApplicationContext private val context: Context,
) : MessageCrypto {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_CRYPTO, Context.MODE_PRIVATE)
    private val peerPublicKeys = ConcurrentHashMap<String, PublicKey>()
    private val sharedAesKeys = ConcurrentHashMap<String, SecretKey>()
    private val secureRandom = SecureRandom()

    @Volatile
    private var activeUsername: String = ""

    @Volatile
    private var myKeyPair: KeyPair? = null

    fun initializeForUser(username: String): String {
        val clean = username.trim().lowercase()
        if (clean.isBlank()) return ""
        if (activeUsername != clean || myKeyPair == null) {
            activeUsername = clean
            sharedAesKeys.clear()
            myKeyPair = loadOrGenerateUserKeyPair(clean)
        }
        return Base64.encodeToString(myKeyPair!!.public.encoded, Base64.NO_WRAP)
    }

    fun getPublicKeyBase64(): String {
        val kp = myKeyPair ?: return ""
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    fun cachePeerPublicKey(peerId: String, publicKeyBase64: String) {
        val clean = peerId.trim().lowercase()
        if (clean.isBlank() || publicKeyBase64.isBlank()) return
        try {
            val encoded = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
            val existing = peerPublicKeys[clean]
            if (existing == null || !existing.encoded.contentEquals(pub.encoded)) {
                peerPublicKeys[clean] = pub
                sharedAesKeys.remove(clean)
            }
        } catch (_: Exception) {}
    }

    fun hasPeerPublicKey(peerId: String): Boolean {
        return peerPublicKeys.containsKey(peerId.trim().lowercase())
    }

    override suspend fun encrypt(recipientId: String, plaintext: ByteArray): ByteArray {
        val cleanRecipient = recipientId.trim().lowercase()
        val aesKey = deriveSharedKey(cleanRecipient)
        return encryptWithAesGcm(aesKey, plaintext)
    }

    override suspend fun decrypt(senderId: String, ciphertext: ByteArray): ByteArray {
        val cleanSender = senderId.trim().lowercase()
        val aesKey = deriveSharedKey(cleanSender)
        return decryptWithAesGcm(aesKey, ciphertext)
    }

    fun encryptWithGroupKey(groupKeyBytes: ByteArray, plaintext: ByteArray): ByteArray {
        val aesKey = SecretKeySpec(groupKeyBytes, "AES")
        return encryptWithAesGcm(aesKey, plaintext)
    }

    fun decryptWithGroupKey(groupKeyBytes: ByteArray, ciphertext: ByteArray): ByteArray {
        val aesKey = SecretKeySpec(groupKeyBytes, "AES")
        return decryptWithAesGcm(aesKey, ciphertext)
    }

    fun generateGroupKey(): ByteArray {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    private fun deriveSharedKey(peerId: String): SecretKey {
        sharedAesKeys[peerId]?.let { return it }
        val localPair = myKeyPair ?: throw IllegalStateException("E2EE keypair not initialized")
        val peerPub = peerPublicKeys[peerId]
            ?: throw IllegalStateException("Missing E2EE public key for $peerId")

        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPair.private)
        agreement.doPhase(peerPub, true)
        val sharedSecret = agreement.generateSecret()

        val derivedBytes = hkdfSha256(
            ikm = sharedSecret,
            salt = "moth-hours-e2ee-salt".toByteArray(Charsets.UTF_8),
            info = "moth-hours-e2ee-v1".toByteArray(Charsets.UTF_8),
            length = 32,
        )
        val key = SecretKeySpec(derivedBytes, "AES")
        sharedAesKeys[peerId] = key
        return key
    }

    private fun encryptWithAesGcm(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(1 + IV_LENGTH_BYTES + encrypted.size)
            .put(VERSION_E2EE_V1)
            .put(iv)
            .put(encrypted)
            .array()
    }

    private fun decryptWithAesGcm(key: SecretKey, payload: ByteArray): ByteArray {
        if (payload.size < 1 + IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Malformed E2EE payload")
        }
        val buffer = ByteBuffer.wrap(payload)
        val version = buffer.get()
        if (version != VERSION_E2EE_V1) {
            throw IllegalArgumentException("Unsupported E2EE version: $version")
        }
        val iv = ByteArray(IV_LENGTH_BYTES)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun encryptWithKeystoreKey(masterKey: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        // AndroidKeyStore forbids caller-supplied IV in ENCRYPT_MODE; let Keystore generate IV.
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(2 + iv.size + encrypted.size)
            .put(VERSION_E2EE_V1)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    private fun decryptWithKeystoreKey(masterKey: SecretKey, payload: ByteArray): ByteArray {
        if (payload.size < 3 + 16) {
            throw IllegalArgumentException("Malformed wrapped key payload")
        }
        val buffer = ByteBuffer.wrap(payload)
        val version = buffer.get()
        if (version != VERSION_E2EE_V1) {
            throw IllegalArgumentException("Unsupported key wrap version: $version")
        }
        val ivLen = buffer.get().toInt() and 0xFF
        if (ivLen <= 0 || ivLen > 32 || buffer.remaining() <= ivLen) {
            throw IllegalArgumentException("Invalid wrapped key IV length: $ivLen")
        }
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    private fun loadOrGenerateUserKeyPair(username: String): KeyPair {
        val pubKeyB64 = prefs.getString("pub_$username", null)
        val wrappedPrivB64 = prefs.getString("priv_wrapped_$username", null)
        if (!pubKeyB64.isNullOrBlank() && !wrappedPrivB64.isNullOrBlank()) {
            try {
                val masterKey = getOrCreateKeystoreMasterKey()
                val rawWrapped = Base64.decode(wrappedPrivB64, Base64.NO_WRAP)
                val privBytes = decryptWithKeystoreKey(masterKey, rawWrapped)
                val kf = KeyFactory.getInstance("EC")
                val priv: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pub: PublicKey = kf.generatePublic(
                    X509EncodedKeySpec(Base64.decode(pubKeyB64, Base64.NO_WRAP))
                )
                return KeyPair(pub, priv)
            } catch (_: Exception) {}
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()
        val masterKey = getOrCreateKeystoreMasterKey()
        val wrappedPriv = encryptWithKeystoreKey(masterKey, kp.private.encoded)

        prefs.edit()
            .putString("pub_$username", Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
            .putString("priv_wrapped_$username", Base64.encodeToString(wrappedPriv, Base64.NO_WRAP))
            .apply()

        return kp
    }

    private fun getOrCreateKeystoreMasterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    companion object {
        private const val PREFS_CRYPTO = "moth_e2ee_keys"
        private const val KEYSTORE_ALIAS = "moth_e2ee_master_aes_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val VERSION_E2EE_V1: Byte = 1
    }
}
