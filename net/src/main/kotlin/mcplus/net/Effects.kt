package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * Один эффект на игроке: то, что прислал сервер, плюс имя из встроенного реестра.
 *
 * Длительность и усилитель — числа сервера: клиент их не считает. `amplifier` — это уровень
 * минус один: 0 — «I», 1 — «II».
 */
data class ActiveEffect(
    val id: Int,
    val name: String,
    val amplifier: Int,
    val durationTicks: Int,
    val ambient: Boolean,
    val showIcon: Boolean,
    val category: EffectCategory,
) {
    /** В ванильном HUD положительные эффекты идут в ряд, остальные — в столбец. */
    val beneficial: Boolean get() = category == EffectCategory.BENEFICIAL
}

/**
 * Эффекты на нашем игроке.
 *
 * Пишет поток сети, читает кадр — все обращения синхронизированы. Пакеты приходят **на всех**
 * сущностей, поэтому фильтр по своему entity id обязателен: без него на HUD повисли бы
 * эффекты любого моба рядом.
 */
class Effects(private val table: EffectTable = EffectTable.EMPTY) {

    private val active = LinkedHashMap<Int, ActiveEffect>()

    val size: Int get() = synchronized(active) { active.size }

    val isEmpty: Boolean get() = synchronized(active) { active.isEmpty() }

    /** Имена из реестра — по ним HUD загружает иконки. */
    val iconNames: List<String> get() = table.names

    fun nameOf(id: Int): String = table.nameOf(id)

    /**
     * `Update Mob Effect` (0x87): entity id, effect id, amplifier, duration, flags.
     * `Remove Mob Effect` (0x4F): entity id и effect id.
     *
     * @return true, если пакет был про нашего игрока и состояние изменилось.
     */
    fun onPacket(packetId: Int, body: ByteArray, selfId: Int): Boolean {
        val reader = ByteReader(body)
        val entityId = reader.readVarInt()
        return when (packetId) {
            Protocol777.Play.UPDATE_MOB_EFFECT -> {
                val effectId = reader.readVarInt()
                val amplifier = reader.readVarInt()
                val duration = reader.readVarInt()
                val flags = reader.readUnsignedByte()
                update(entityId, effectId, amplifier, duration, flags, selfId)
            }

            Protocol777.Play.REMOVE_MOB_EFFECT -> remove(entityId, reader.readVarInt(), selfId)
            else -> false
        }
    }

    internal fun update(entityId: Int, effectId: Int, amplifier: Int, duration: Int, flags: Int, selfId: Int): Boolean {
        if (entityId != selfId) return false
        val info = table.infoOf(effectId)
        val effect = ActiveEffect(
            id = effectId,
            name = info?.name ?: "effect#$effectId",
            amplifier = amplifier,
            durationTicks = duration,
            ambient = flags and FLAG_AMBIENT != 0,
            showIcon = flags and FLAG_SHOW_ICON != 0,
            category = info?.category ?: EffectCategory.NEUTRAL,
        )
        synchronized(active) { active[effectId] = effect }
        return true
    }

    internal fun remove(entityId: Int, effectId: Int, selfId: Int): Boolean {
        if (entityId != selfId) return false
        synchronized(active) { active.remove(effectId) }
        return true
    }

    /** Снимок для кадра: список неизменяем, сеть может менять карту дальше. */
    fun snapshot(): List<ActiveEffect> = synchronized(active) { active.values.toList() }

    fun clear() = synchronized(active) { active.clear() }

    private companion object {
        const val FLAG_AMBIENT = 1
        const val FLAG_SHOW_ICON = 4
    }
}
