package mcplus.net

import mcplus.protocol.Packet
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MinecraftHashTest {

    @Test
    fun `signed hex matches the reference vectors`() {
        assertEquals("4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48", hexOf("Notch"))
        assertEquals("-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1", hexOf("jeb_"))
        assertEquals("88e16a1019277b15d58faf0541e11910eb756f6", hexOf("simon"))
    }

    @Test
    fun `server hash combines id, secret and key`() {
        val serverId = ""
        val secret = ByteArray(16) { (it * 3).toByte() }
        val key = ByteArray(162) { (it % 7).toByte() }

        val expected = MinecraftHash.signedHex(
            minecraftSha1(serverId.toByteArray(Charsets.ISO_8859_1) + secret + key),
        )
        assertEquals(expected, MinecraftHash.serverHash(serverId, secret, key))
    }

    private fun hexOf(text: String): String = MinecraftHash.signedHex(minecraftSha1(text.toByteArray(Charsets.ISO_8859_1)))

    private fun minecraftSha1(data: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-1").digest(data)
}

class RsaTest {

    @Test
    fun `rsa round trip of a shared secret`() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(1024)
        val pair = generator.generateKeyPair()
        val der = pair.public.encoded

        val secret = ByteArray(16) { it.toByte() }
        val encrypted = Rsa.encrypt(der, secret)

        assertEquals(128, encrypted.size, "PKCS#1 padding of a 1024-bit key gives 128 bytes")

        val decrypted = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, pair.private)
            doFinal(encrypted)
        }
        assertContentEquals(secret, decrypted)
    }
}

class AesCfb8Test {

    @Test
    fun `cipher keeps state across updates`() {
        val secret = ByteArray(16) { (it * 5).toByte() }
        val keySpec = SecretKeySpec(secret, "AES")
        val encrypt = Cipher.getInstance("AES/CFB8/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(secret)) }
        val decrypt = Cipher.getInstance("AES/CFB8/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(secret)) }

        val first = "first packet".toByteArray()
        val second = "second packet".toByteArray()

        val cipherFirst = encrypt.update(first)
        val cipherSecond = encrypt.update(second)

        assertContentEquals(first, decrypt.update(cipherFirst))
        assertContentEquals(second, decrypt.update(cipherSecond))
    }
}

class EncryptedConnectionTest {

    @Test
    fun `two connections share an encrypted stream`() {
        val secret = ByteArray(16) { (it * 7).toByte() }
        val receivedByServer = AtomicReference<String?>()

        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                server.accept().use { socket ->
                    Connection(socket).use { connection ->
                        connection.enableEncryption(secret)
                        receivedByServer.set(String(connection.receive().body))
                        connection.send(Packet(2, "pong".toByteArray()))
                    }
                }
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
                Connection(socket).use { connection ->
                    connection.enableEncryption(secret)
                    connection.send(Packet(1, "ping".toByteArray()))
                    assertEquals("pong", String(connection.receive().body))
                }
            }
            serverThread.join(5_000)
        }
        assertNotNull(receivedByServer.get())
        assertEquals("ping", receivedByServer.get())
    }
}
