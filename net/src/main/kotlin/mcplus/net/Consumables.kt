package mcplus.net

import java.io.File

/** Что делает предмет при использовании: едят, пьют или что-то ещё (рог, фейерверк). */
enum class ConsumableAnimation { EAT, DRINK, OTHER }

/** Описание предмета с анимацией использования. */
data class Consumable(
    val item: String,
    val animation: ConsumableAnimation,
    val sound: String,
    val seconds: Float,
    val particles: Boolean,
)

/**
 * Предметы, которые едят и пьют.
 *
 * Пакет `Use Item` не говорит, какую анимацию показывать: он несёт только руку и углы.
 * Признак «это еда» живёт в компоненте `minecraft:consumable` самого предмета, поэтому
 * таблица собирается из отчёта генератора данных игры
 * ([tools/build-consumable-table.js]) — угадывать по имени нельзя.
 */
class Consumables(private val byName: Map<String, Consumable>) {

    fun of(itemName: String): Consumable? = byName[itemName.removePrefix("minecraft:")]

    val size: Int get() = byName.size

    companion object {
        val EMPTY = Consumables(emptyMap())

        fun load(file: File): Consumables {
            val rows = HashMap<String, Consumable>()
            file.forEachLine { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachLine
                val parts = line.split('\t')
                val item = parts.getOrNull(0)?.removePrefix("minecraft:") ?: return@forEachLine
                val animation = when (parts.getOrNull(1)) {
                    "eat" -> ConsumableAnimation.EAT
                    "drink" -> ConsumableAnimation.DRINK
                    else -> ConsumableAnimation.OTHER
                }
                val sound = parts.getOrNull(2)?.removePrefix("minecraft:").orEmpty()
                val seconds = parts.getOrNull(3)?.toFloatOrNull() ?: 1.6f
                val particles = parts.getOrNull(4)?.trim() != "0"
                rows[item] = Consumable(item, animation, sound, seconds, particles)
            }
            return Consumables(rows)
        }
    }
}
