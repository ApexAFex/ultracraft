package mcplus.net

import java.util.concurrent.ConcurrentHashMap
import mcplus.protocol.ByteReader
import mcplus.protocol.nbt.NbtCompound
import mcplus.protocol.nbt.NbtReader
import mcplus.protocol.nbt.NbtString
import mcplus.protocol.nbt.int
import mcplus.protocol.nbt.boolean
import mcplus.protocol.nbt.float
import mcplus.protocol.nbt.compound
import mcplus.protocol.nbt.string

/**
 * Едущий поршень: блок, который сейчас движется, и насколько он уже выехал.
 *
 * Сервер присылает это пакетом `Block Entity Data` (NBT: `blockState`, `facing`, `progress`,
 * `extending`, `source`). Раньше пакет разбирался только ради табличек, поэтому поршни на
 * экране стояли на месте, а точнее — их вообще не было видно: блок `moving_piston` не имеет
 * своей модели.
 *
 * `progress` идёт от 0 к 1, `facing` — номер направления (0 — вниз, 1 — вверх, 2 — север,
 * 3 — юг, 4 — запад, 5 — восток).
 */
data class MovingPiston(
    val x: Int,
    val y: Int,
    val z: Int,
    /** Имя движущегося блока: `piston_head`, `stone` и так далее. */
    val blockName: String,
    val facing: Int,
    val progress: Float,
    val extending: Boolean,
    /** Липкая головка: у неё своя текстура (piston_top_sticky). */
    val sticky: Boolean = false,
) {
    /** Головка ли это поршня (а не толкаемый блок). */
    val isHead: Boolean
        get() = blockName.endsWith("piston_head") || blockName == "moving_piston"

    /**
     * Имя блока, из которого брать текстуры для отрисовки.
     *
     * У головки своей текстуры в атласе нет: она составная (верх поршня + бока). Поэтому
     * головка рисуется текстурами блока поршня, а липкая — липкого.
     */
    fun textureBlock(): String = when {
        !isHead -> blockName
        sticky -> "sticky_piston"
        else -> "piston"
    }
    /**
     * Насколько блок выехал от своего места: 0 — у основания, 1 — на месте.
     *
     * В ванили `PistonMovingBlockEntity.getExtendedProgress`: при выдвижении
     * `progress - 1`, при втягивании `1 - progress`, потому что `progress` отсчитывается от
     * конечного положения.
     */
    val extendedProgress: Float
        get() = if (extending) progress - 1f else 1f - progress

    /** Смещение центра от центра клетки в блоках: направление × выезд. */
    fun offsetX(): Float = directionX(facing) * extendedProgress

    fun offsetY(): Float = directionY(facing) * extendedProgress

    fun offsetZ(): Float = directionZ(facing) * extendedProgress

    /**
     * Размер плиты головки поршня: в ванили это пластина толщиной 4 пикселя (0.25 блока).
     *
     * Возвращает размеры по осям, чтобы тонкая сторона смотрела по направлению движения.
     */
    fun headSize(): Triple<Float, Float, Float> = when (facing) {
        // Вверх и вниз — тонкая по Y.
        0, 1 -> Triple(1f, HEAD_THICKNESS, 1f)
        // Запад и восток — тонкая по X.
        4, 5 -> Triple(HEAD_THICKNESS, 1f, 1f)
        // Север и юг — тонкая по Z.
        else -> Triple(1f, 1f, HEAD_THICKNESS)
    }

    companion object {
        const val HEAD_THICKNESS = 0.25f

        fun directionX(facing: Int): Float = when (facing) {
            4 -> -1f
            5 -> 1f
            else -> 0f
        }

        fun directionY(facing: Int): Float = when (facing) {
            0 -> -1f
            1 -> 1f
            else -> 0f
        }

        fun directionZ(facing: Int): Float = when (facing) {
            2 -> -1f
            3 -> 1f
            else -> 0f
        }
    }
}

/**
 * Собирает едущие поршни из пакетов `Block Entity Data`.
 *
 * Пакет общий для всех блок-сущностей, поэтому поршень узнаётся по набору полей: у него есть
 * и `blockState`, и `progress`, и `facing`. Таблички и сундуки этих полей не имеют.
 */
object Pistons {

    private val moving = ConcurrentHashMap<Long, MovingPiston>()

    /** Пакет `Block Entity Data`: позиция, тип, NBT. */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        if (packetId != mcplus.net.proto.Protocol777.Play.BLOCK_ENTITY_DATA) return false
        runCatching {
            val reader = ByteReader(body)
            val position = reader.readPosition()
            reader.readVarInt() // тип блок-сущности
            accept(position.x, position.y, position.z, NbtReader.read(reader))
        }
        return true
    }

    /** Разбирает NBT поршня; для остальных блок-сущностей ничего не делает. */
    fun accept(x: Int, y: Int, z: Int, tag: mcplus.protocol.nbt.NbtTag?) {
        val compound = tag as? NbtCompound ?: return
        val piston = parse(x, y, z, compound) ?: return
        // Защита от утечки: сервер может прислать много записей, а забывать их мы умеем
        // только по смене блока. Больше нескольких сотен одновременно не бывает.
        if (moving.size >= MAX_TRACKED) moving.clear()
        moving[key(x, y, z)] = piston
        seen++
    }

    /** Сколько едущих поршней пришло за запуск: для отчёта в конце. */
    var seen = 0
        private set

    /** Разбор NBT; вынесен отдельно, чтобы проверять его тестом без сети. */
    internal fun parse(x: Int, y: Int, z: Int, compound: NbtCompound): MovingPiston? {
        val state = compound["blockState"] as? NbtCompound ?: return null
        val name = state.string("Name") ?: return null
        val facing = compound.int("facing") ?: return null
        val progress = compound.float("progress") ?: return null
        // Свойства блока лежат во вложенном compound Properties; для головки важно 	ype.
        val properties = state.compound("Properties")
        val sticky = properties?.string("type") == "sticky"
        return MovingPiston(
            x = x,
            y = y,
            z = z,
            blockName = name.removePrefix("minecraft:"),
            facing = facing,
            progress = progress.coerceIn(0f, 1f),
            extending = compound.boolean("extending") ?: true,
            sticky = sticky,
        )
    }

    fun snapshot(): List<MovingPiston> = moving.values.toList()

    /** Поршень доехал или его блок сменился: запись больше не нужна. */
    fun forget(x: Int, y: Int, z: Int) {
        moving.remove(key(x, y, z))
    }

    fun clear() = moving.clear()

    /** Сколько едущих поршней держим одновременно. */
    private const val MAX_TRACKED = 512

    private fun key(x: Int, y: Int, z: Int): Long =
        (x.toLong() and 0x3FFFFFF) shl 38 or
            ((z.toLong() and 0x3FFFFFF) shl 12) or
            (y.toLong() and 0xFFF)
}
