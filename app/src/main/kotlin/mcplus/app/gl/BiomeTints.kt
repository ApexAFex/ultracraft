package mcplus.app.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage

/**
 * Цвета одного биома: трава, листва, вода. Берутся из `effects` файла биома; если явного
 * цвета нет — из цветовой карты по температуре и влажности.
 */
data class BiomeTint(
    val grass: Int,
    val foliage: Int,
    val water: Int,
    val fog: Int? = null,
    /** Базовая температура биома: по ней ваниль решает, снег идёт или дождь. */
    val temperature: Float = 0.8f,
)

/**
 * Оттенки биомов для травы, листвы и воды.
 *
 * Биом приходит по сети номером, а цвета — из данных игры: `data/minecraft/worldgen/biome/
 * <биом>.json` (`effects.grass_color`, `foliage_color`, `water_color`, модификаторы) и
 * `textures/colormap/grass.png` для биомов без явного цвета — там оттенок считается по
 * температуре и влажности. Всё это лежит в jar игрока, ничего не копируется.
 *
 * В атлас текстуры уже запечены с базовым оттенком ([TextureAtlas.TINT_GRASS] и т. п.),
 * поэтому меш получает **отношение** цвета биома к базовому — так иконки предметов остаются
 * цветными, а мир получает оттенок своего биома.
 */
class BiomeTints private constructor(
    /** Множители по id биома для трёх видов: трава, листва, вода. */
    private val ratios: List<Array<FloatArray>?>,
    /** Цвет тумана биома или null, если его нет. */
    private val fogs: List<Int?> = emptyList(),
    /** Базовая температура биома по id: ниже 0,15 ваниль сыплет снег. */
    private val temperatures: List<Float?> = emptyList(),
) {

    /** Множитель цвета для [kind] в биоме [biomeId]; 1 — оставить как в атласе. */
    fun ratio(kind: Int, biomeId: Int): FloatArray =
        ratios.getOrNull(biomeId)?.getOrNull(kind) ?: NEUTRAL

    /** Температура биома из данных игры или null, если биом не загрузился. */
    fun temperatureOf(biomeId: Int): Float? = temperatures.getOrNull(biomeId)

    /** Цвет тумана биома, если он задан в данных игры. */
    fun fogOf(biomeId: Int): Int? = fogs.getOrNull(biomeId)

    companion object {
        const val GRASS = 0
        const val FOLIAGE = 1
        const val WATER = 2

        private val NEUTRAL = floatArrayOf(1f, 1f, 1f)

        val DEFAULT = BiomeTints(emptyList())

        /**
         * @param biomeNames имена биомов в порядке id из реестра (тот же порядок, что в секции).
         */
        fun load(directory: java.io.File?, biomeNames: List<String>): BiomeTints {
            if (biomeNames.isEmpty() || directory == null) return DEFAULT
            val grassMap = GameFiles.readPng(directory, "assets/minecraft/textures/colormap/grass.png")
            val foliageMap = GameFiles.readPng(directory, "assets/minecraft/textures/colormap/foliage.png")

            val ratios = ArrayList<Array<FloatArray>?>(biomeNames.size)
            val fogs = ArrayList<Int?>(biomeNames.size)
            val temperatures = ArrayList<Float?>(biomeNames.size)
            for (name in biomeNames) {
                val text = GameFiles.readText(directory, "data/minecraft/worldgen/biome/$name.json")
                val tint = text?.let { parse(it, grassMap, foliageMap) }
                ratios += tint?.let { arrayOf(ratio(it.grass, TextureAtlas.TINT_GRASS), ratio(it.foliage, TextureAtlas.TINT_FOLIAGE), ratio(it.water, TextureAtlas.TINT_WATER)) }
                fogs += tint?.fog
                temperatures += tint?.temperature
            }
            return BiomeTints(ratios, fogs, temperatures)
        }

        

        /** Разбор одного файла биома; внутренний, чтобы проверяться тестом без установки игры. */
        internal fun parse(text: String, grassMap: BufferedImage?, foliageMap: BufferedImage?): BiomeTint? {
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val temperature = (json["temperature"] ?: return null).jsonPrimitive.doubleOrNull ?: return null
            val downfall = (json["downfall"] ?: return null).jsonPrimitive.doubleOrNull ?: return null
            val effects = json["effects"] as? JsonObject ?: JsonObject(emptyMap())

            var grass = effects.color("grass_color") ?: sample(grassMap, temperature, downfall) ?: TextureAtlas.TINT_GRASS
            val foliage = effects.color("foliage_color") ?: sample(foliageMap, temperature, downfall) ?: TextureAtlas.TINT_FOLIAGE
            val water = effects.color("water_color") ?: TextureAtlas.TINT_WATER

            // Модификаторы ванильные: болото затемняет траву, тёмный лес подмешивает почти чёрный.
            when (effects.string("grass_color_modifier")) {
                "swamp" -> grass = 0x6A7039
                "dark_forest" -> grass = average(grass, 0x28340A)
            }
            return BiomeTint(grass, foliage, water, effects.color("fog_color"), temperature.toFloat())
        }

        private fun JsonObject.color(key: String): Int? = string(key)?.removePrefix("#")?.toIntOrNull(16)

        private fun JsonObject.string(key: String): String? =
            (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

        /** Пиксель цветовой карты: ось X — температура, ось Y — влажность. */
        private fun sample(map: BufferedImage?, temperature: Double, downfall: Double): Int? {
            if (map == null) return null
            val x = ((1.0 - temperature) * 255.0).toInt().coerceIn(0, 255)
            val y = ((1.0 - downfall) * 255.0).toInt().coerceIn(0, 255)
            return map.getRGB(x, y) and 0xFFFFFF
        }

        private fun average(first: Int, second: Int): Int {
            val r = (((first shr 16) and 0xFF) + ((second shr 16) and 0xFF)) / 2
            val g = (((first shr 8) and 0xFF) + ((second shr 8) and 0xFF)) / 2
            val b = ((first and 0xFF) + (second and 0xFF)) / 2
            return (r shl 16) or (g shl 8) or b
        }

        /** Отношение цвета биома к уже запечённому в атлас, чтобы шейдер домножил. */
        private fun ratio(color: Int, baked: Int): FloatArray = floatArrayOf(
            (((color shr 16) and 0xFF) + 1f) / (((baked shr 16) and 0xFF) + 1f),
            (((color shr 8) and 0xFF) + 1f) / (((baked shr 8) and 0xFF) + 1f),
            ((color and 0xFF) + 1f) / ((baked and 0xFF) + 1f),
        )
    }
}

