package mcplus.app.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.image.BufferedImage

/**
 * Покадровая анимация текстур (вода, лава, огонь, портал).
 *
 * В игре такие текстуры лежат вертикальной лентой кадров, а `.png.mcmeta` рядом говорит,
 * сколько тиков держать кадр и в каком порядке их показывать. Без этого вода и лава стоят
 * как фотография — самое заметное отличие от оригинала.
 *
 * Здесь только арифметика: какие кадры и по сколько тиков. Сами картинки и загрузку в
 * видеопамять держат `TextureAtlas` и `GlTextures`, поэтому расчёт проверяется тестами без
 * видеокарты.
 */
object TextureAnimation {

    /**
     * Кадры одной текстуры: картинки в порядке показа и длительность каждой в тиках.
     *
     * [frameAt] переводит номер тика в номер кадра; отрицательное время и переполнение не
     * ломают счёт, поэтому анимация продолжается даже после долгой паузы окна.
     */
    class Frames(val frames: List<BufferedImage>, val frameTicks: List<Int>) {

        val totalTicks: Int = frameTicks.sum().coerceAtLeast(1)

        fun frameAt(tick: Long): Int {
            if (frames.isEmpty()) return 0
            var rest = ((tick % totalTicks) + totalTicks) % totalTicks
            for ((index, ticks) in frameTicks.withIndex()) {
                if (rest < ticks) return index
                rest -= ticks
            }
            return frames.lastIndex
        }
    }

    /**
     * Разбирает `.png.mcmeta`: возвращает порядок кадров (номера в ленте) и время показа
     * каждого в тиках. Формат: `{"animation":{"frametime":2,"frames":[0,1,{"index":5,"time":3}]}}`.
     *
     * Всё, что не указано, берётся по умолчанию: один тик на кадр, кадры по порядку.
     * Номера за пределами ленты отбрасываются — битый файл не должен ронять клиент.
     */
    fun parse(json: String?, frameCount: Int): Pair<List<Int>, List<Int>> {
        val all = (0 until frameCount).toList()
        val defaultTimes = List(frameCount) { 1 }
        if (json.isNullOrBlank() || frameCount <= 1) return all to defaultTimes

        val animation = runCatching { Json.parseToJsonElement(json) }
            .getOrNull()?.asObject()?.get("animation")?.asObject()
            ?: return all to defaultTimes
        val frametime = animation["frametime"].asInt()?.coerceAtLeast(1) ?: 1

        val frames = animation["frames"] as? JsonArray
            ?: return all to List(frameCount) { frametime }

        val order = ArrayList<Int>(frames.size)
        val times = ArrayList<Int>(frames.size)
        for (frame in frames) {
            when (frame) {
                is JsonPrimitive -> {
                    val index = frame.content.toIntOrNull() ?: continue
                    if (index in 0 until frameCount) {
                        order += index
                        times += frametime
                    }
                }

                is JsonObject -> {
                    val index = frame["index"].asInt() ?: continue
                    if (index in 0 until frameCount) {
                        order += index
                        times += frame["time"].asInt()?.coerceAtLeast(1) ?: frametime
                    }
                }

                else -> continue
            }
        }
        if (order.isEmpty()) return all to List(frameCount) { frametime }
        return order to times
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.content?.toIntOrNull()
}
