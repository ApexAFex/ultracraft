package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Время суток и погода — то, из чего клиент считает свет неба, цвет неба и туман.
 *
 * С 26.x время приходит не одним числом: `Set Time` несёт карту «часы мира → состояние»,
 * где часы заданы числовым id реестра `world_clock`. День и ночь определяет часы
 * `overworld`; если сервер прислал только их — берём единственную запись.
 */
class SkyState {

    /** Время суток в тиках 0..23999: 0 — рассвет, 6000 — полдень, 18000 — полночь. */
    var dayTicks: Long = 0L
        private set

    /** Дробная часть тика: нужна только для плавности в будущем. */
    var partialTick: Float = 0f
        private set

    /** Скорость хода часов; 0 — время остановлено сервером. */
    var rate: Float = 1f
        private set

    /** Уровень дождя 0..1. */
    var rain: Float = 0f
        private set

    /** Уровень грозы 0..1. */
    var thunder: Float = 0f
        private set

    fun onPacket(packetId: Int, body: ByteArray, clockName: (Int) -> String?): Boolean {
        when (packetId) {
            Protocol777.Play.SET_TIME -> runCatching { readTime(ByteReader(body), clockName) }
            Protocol777.Play.GAME_EVENT -> runCatching { readGameEvent(ByteReader(body)) }
            else -> return false
        }
        return true
    }

    /** `Set Time`: long gameTime, затем VarInt count и записи «id часов, тики, доля, скорость». */
    private fun readTime(reader: ByteReader, clockName: (Int) -> String?) {
        reader.readLong()
        val count = reader.readVarInt()
        if (count !in 0..8) return

        var overworld: Triple<Long, Float, Float>? = null
        var first: Triple<Long, Float, Float>? = null
        repeat(count) {
            val clockId = reader.readVarInt()
            val ticks = reader.readVarLong()
            val partial = reader.readFloat()
            val rate = reader.readFloat()
            val entry = Triple(ticks, partial, rate)
            if (first == null) first = entry
            if (clockName(clockId) == "overworld") overworld = entry
        }

        val entry = overworld ?: first ?: return
        dayTicks = Math.floorMod(entry.first, TICKS_PER_DAY)
        partialTick = entry.second
        rate = entry.third
    }

    /** `Game Event`: id события байтом и значение float. Дождь и гроза — события 1, 2, 7, 8. */
    private fun readGameEvent(reader: ByteReader) {
        val event = reader.readUnsignedByte()
        val value = reader.readFloat()
        when (event) {
            START_RAINING -> rain = 1f
            STOP_RAINING -> rain = 0f
            RAIN_LEVEL_CHANGE -> rain = value.coerceIn(0f, 1f)
            THUNDER_LEVEL_CHANGE -> thunder = value.coerceIn(0f, 1f)
        }
    }

    companion object {
        const val TICKS_PER_DAY = 24_000L
        private const val START_RAINING = 1
        private const val STOP_RAINING = 2
        private const val RAIN_LEVEL_CHANGE = 7
        private const val THUNDER_LEVEL_CHANGE = 8
    }
}
