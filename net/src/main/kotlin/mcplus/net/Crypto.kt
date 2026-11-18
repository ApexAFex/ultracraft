package mcplus.net

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Minecraft's non-standard "signed hex" digest used when joining the session server.
 *
 * The SHA-1 result is read as one big two's-complement integer and printed in base 16,
 * with a leading minus for negative values — unlike the usual unsigned hex digest.
 */
object MinecraftHash {

    fun serverHash(serverId: String, sharedSecret: ByteArray, publicKeyDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(serverId.toByteArray(Charsets.ISO_8859_1))
        digest.update(sharedSecret)
        digest.update(publicKeyDer)
        return signedHex(digest.digest())
    }

    fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

    fun signedHex(digest: ByteArray): String = BigInteger(digest).toString(16)
}

/** RSA helpers for the login handshake. */
object Rsa {

    private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"

    fun publicKey(der: ByteArray): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))

    /** PKCS#1 v1.5 padding: the 16-byte secret becomes a 128-byte block for a 1024-bit key. */
    fun encrypt(publicKeyDer: ByteArray, data: ByteArray): ByteArray =
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, publicKey(publicKeyDer))
            doFinal(data)
        }
}
