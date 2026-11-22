package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Здоровье, голод и опыт в том виде, в каком их присылает сервер.
 *
 * Клиент ничего из этого не считает: урон, голод, насыщение и опыт — серверные числа, здесь
 * они только хранятся, чтобы HUD было что показывать. Значения читаются из пакетов:
 * `Set Health` (0x6A), `Set Experience` (0x69) и `Respawn` (0x54).
 *
 * Смерть определяется по здоровью: сервер выставляет его в ноль и перестаёт стримить мир
 * (см. `Session.requestRespawn`), поэтому отдельного флага «мёртв» не нужно.
 */
class Vitals {

    /** Здоровье в очках: 20 — полная шкала, 10 сердец. */
    var health: Float = MAX_HEALTH
        private set

    /** Голод в очках 0..20 (в HUD это 10 значков еды). */
    var food: Int = MAX_FOOD
        private set

    /** Насыщение: сервер присылает его попутно, на HUD пока не влияет. */
    var saturation: Float = DEFAULT_SATURATION
        private set

    /** Прогресс полосы опыта 0..1. */
    var xpProgress: Float = 0f
        private set

    /** Уровень опыта. */
    var level: Int = 0
        private set

    /** Всего опыта; нужен для отладочного вывода, HUD показывает только уровень. */
    var xpTotal: Int = 0
        private set

    val dead: Boolean get() = health <= 0f

    /**
     * @return true, если пакет относился к состоянию игрока (тогда вызывающий его не разбирает).
     *
     * Битое тело не должно ронять соединение: пакет прочитан, просто поля остаются прежними.
     */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        when (packetId) {
            Protocol777.Play.SET_HEALTH -> runCatching { readHealth(ByteReader(body)) }
            Protocol777.Play.SET_EXPERIENCE -> runCatching { readExperience(ByteReader(body)) }
            Protocol777.Play.RESPAWN -> respawned()
            else -> return false
        }
        return true
    }

    /** `Set Health`: float health, VarInt food, float saturation — в этом порядке. */
    private fun readHealth(reader: ByteReader) {
        health = reader.readFloat()
        food = reader.readVarInt()
        saturation = reader.readFloat()
    }

    /** `Set Experience`: float progress (0..1), VarInt level, VarInt total. */
    private fun readExperience(reader: ByteReader) {
        xpProgress = reader.readFloat().coerceIn(0f, 1f)
        level = reader.readVarInt()
        xpTotal = reader.readVarInt()
    }

    /**
     * `Respawn`: сервер подтвердил возрождение.
     *
     * Настоящие значения придут следующим `Set Health`, но между этими пакетами HUD успел бы
     * показать ноль сердец. Поэтому здоровье и голод ставятся на максимум сразу.
     */
    fun respawned() {
        health = MAX_HEALTH
        food = MAX_FOOD
        saturation = DEFAULT_SATURATION
    }

    companion object {
        const val MAX_HEALTH = 20f
        const val MAX_FOOD = 20
        const val DEFAULT_SATURATION = 5f

        /** Предел насыщения: игра не даёт ему обогнать уровень еды, то есть 20. */
        const val MAX_SATURATION = 20f
    }
}
