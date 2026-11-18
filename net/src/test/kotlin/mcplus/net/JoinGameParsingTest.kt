package mcplus.net

import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinGameParsingTest {

    private fun sampleJoinGame(hasDeathLocation: Boolean = false): ByteArray = ByteWriter()
        .writeInt(1)                       // entity id
        .writeBoolean(false)               // hardcore
        .writeVarInt(3)                    // dimension names
        .writeString("minecraft:overworld")
        .writeString("minecraft:the_nether")
        .writeString("minecraft:the_end")
        .writeVarInt(10)                   // max players
        .writeVarInt(8)                    // view distance
        .writeVarInt(6)                    // simulation distance
        .writeBoolean(false)               // reduced debug info
        .writeBoolean(true)                // respawn screen
        .writeBoolean(false)               // limited crafting
        .writeVarInt(0)                    // dimension type id
        .writeString("minecraft:overworld")
        .writeLong(123456789L)             // hashed seed
        .writeByte(1)                      // game mode: creative
        .writeByte(-1)                     // previous game mode: none
        .writeBoolean(false)               // debug world
        .writeBoolean(true)                // flat world
        .writeBoolean(hasDeathLocation)
        .apply {
            if (hasDeathLocation) {
                writeString("minecraft:overworld")
                writePosition(10, 64, -10)
            }
        }
        .writeVarInt(0)                    // portal cooldown
        .writeVarInt(63)                   // sea level
        .writeBoolean(false)               // online mode
        .writeBoolean(true)                // enforces secure chat
        .toByteArray()

    @Test
    fun `parses every field of join game`() {
        val game = readJoinGame(ByteReader(sampleJoinGame()))

        assertEquals(1, game.entityId)
        assertFalse(game.hardcore)
        assertEquals(listOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), game.dimensionNames)
        assertEquals(10, game.maxPlayers)
        assertEquals(8, game.viewDistance)
        assertEquals(6, game.simulationDistance)
        assertTrue(game.enableRespawnScreen)
        assertEquals("minecraft:overworld", game.dimensionName)
        assertEquals(123456789L, game.hashedSeed)
        assertEquals(1, game.gameMode)
        assertEquals("творческий", game.gameModeName)
        assertEquals(-1, game.previousGameMode)
        assertTrue(game.flatWorld)
        assertNull(game.deathDimension)
        assertEquals(63, game.seaLevel)
        assertFalse(game.onlineMode)
        assertTrue(game.enforcesSecureChat)
    }

    @Test
    fun `parses optional death location`() {
        val game = readJoinGame(ByteReader(sampleJoinGame(hasDeathLocation = true)))
        assertEquals("minecraft:overworld", game.deathDimension)
    }

    @Test
    fun `rejects absurd dimension count`() {
        val body = ByteWriter().writeInt(1).writeBoolean(false).writeVarInt(9999).toByteArray()
        kotlin.test.assertFailsWith<mcplus.protocol.ProtocolException> { readJoinGame(ByteReader(body)) }
    }

    @Test
    fun `parses login success profile with properties`() {
        val body = ByteWriter()
            .writeUuid(java.util.UUID.fromString("9667b029-256c-3a6a-bc5f-9b65783b13f4"))
            .writeString("mcplus")
            .writeVarInt(1)                 // one property (skin)
            .writeString("textures")
            .writeString("base64-blob")
            .writeBoolean(true)
            .writeString("signature")
            .writeUuid(java.util.UUID.randomUUID()) // session id
            .toByteArray()

        val reader = ByteReader(body)
        val profile = readGameProfile(reader)

        assertEquals("mcplus", profile.name)
        assertEquals("9667b029-256c-3a6a-bc5f-9b65783b13f4", profile.uuid.toString())
        // only the session id must remain
        assertEquals(16, reader.remaining)
    }

    @Test
    fun `decodes a json disconnect reason`() {
        val body = ByteWriter()
            .writeString("""{"text":"You are not white-listed on this server!","color":"red"}""")
            .toByteArray()
        assertEquals("You are not white-listed on this server!", decodeReason(body))
    }

    @Test
    fun `summarises a registry packet header`() {
        val body = ByteWriter()
            .writeString("minecraft:worldgen/biome")
            .writeVarInt(64)
            .toByteArray()
        val summary = readRegistrySummary(ByteReader(body), body.size)
        assertEquals("minecraft:worldgen/biome", summary.id)
        assertEquals(body.size, summary.payloadBytes)
    }
}
