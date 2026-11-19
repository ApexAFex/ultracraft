package mcplus.app.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Индексы слоёв атласа для трёх видов граней блока. */
data class FaceTextures(val top: Int, val bottom: Int, val side: Int)

/**
 * Текстуры блоков из ванильных моделей.
 *
 * До этого текстура угадывалась по имени блока: `oak_stairs_side`, `oak_stairs`… Так работает
 * для камня и досок, но у 600 блоков из 1286 (ступени, плиты, заборы, двери, факелы) файла с
 * таким именем нет — они рисовались клетчатой заглушкой `missing`. Настоящий путь — тот же,
 * что у игры: `blockstates/<блок>.json` → модель → её родители → имена текстур.
 *
 * Геометрия моделей здесь не используется: клиент пока рисует куб. Но текстуры берутся по
 * направлениям граней (`up`, `down`, `north`…), поэтому у ступеней и плит появляется доска,
 * а не заглушка.
 */
object BlockModels {

    /** @param byName слои для трёх граней; блоков без модели в карте нет. */
    class Result(val byName: Map<String, FaceTextures>, val resolved: Int) {
        fun of(blockName: String): FaceTextures? = byName[blockName]
    }

    /**
     * @param blockState текст `blockstates/<блок>.json` или null.
     * @param modelJson текст `models/<модель>.json` по id вида `block/oak_stairs`.
     * @param layerOf слой атласа по имени текстуры вида `oak_planks` (без `block/`).
     */
    fun build(
        blockStates: Map<String, String>,
        modelJson: (String) -> String?,
        layerOf: (String) -> Int?,
    ): Result {
        val cache = HashMap<String, ResolvedModel?>()
        val byName = HashMap<String, FaceTextures>(blockStates.size)

        for ((block, stateText) in blockStates) {
            val faces = runCatching { resolveBlock(block, stateText, modelJson, layerOf, cache) }
                .getOrNull() ?: continue
            byName[block] = faces
        }
        return Result(byName, byName.size)
    }

    private fun resolveBlock(
        block: String,
        stateText: String,
        modelJson: (String) -> String?,
        layerOf: (String) -> Int?,
        cache: HashMap<String, ResolvedModel?>,
    ): FaceTextures? {
        val state = Json.parseToJsonElement(stateText).jsonObject
        val modelId = firstModelId(state) ?: return null
        val model = loadModel(modelId, modelJson, cache) ?: return null

        val top = faceTexture(model, "up", listOf("top", "all", "particle"), layerOf)
        val bottom = faceTexture(model, "down", listOf("bottom", "top", "all", "particle"), layerOf)
        val side = faceTexture(
            model, "north",
            listOf("side", "north", "all", "particle"),
            layerOf,
        ) ?: faceTexture(model, "south", listOf("side", "south", "all", "particle"), layerOf)
            ?: faceTexture(model, "east", listOf("side", "east", "all", "particle"), layerOf)
            ?: faceTexture(model, "west", listOf("side", "west", "all", "particle"), layerOf)

        if (top == null || bottom == null || side == null) return null
        return FaceTextures(top, bottom, side)
    }

    /** Первая модель блока: `variants` или первая часть `multipart`. */
    private fun firstModelId(state: JsonObject): String? {
        val variants = state["variants"] as? JsonObject
        if (variants != null) {
            for ((_, value) in variants) {
                val model = modelIdIn(value)
                if (model != null) return model
            }
        }
        val parts = state["multipart"] as? JsonArray ?: return null
        for (part in parts) {
            val apply = (part as? JsonObject)?.get("apply") ?: continue
            val model = modelIdIn(apply)
            if (model != null) return model
        }
        return null
    }

    /** `{"model": "..."}` или `[{"model": "..."}]` — модель первой записи. */
    private fun modelIdIn(value: kotlinx.serialization.json.JsonElement?): String? {
        val element = when (value) {
            is JsonArray -> value.firstOrNull()
            else -> value
        } ?: return null
        val text = (element as? JsonObject)?.get("model")?.jsonPrimitive?.contentOrNullOrEmpty() ?: return null
        return text.removePrefix("minecraft:")
    }

    private fun JsonPrimitive.contentOrNullOrEmpty(): String? =
        if (isString) content else null

    /** Модель с уже склеенными текстурами и гранями (элементы наследуются от родителя). */
    private class ResolvedModel(
        val textures: Map<String, String>,
        val elements: JsonArray?,
    )

    private fun loadModel(
        id: String,
        modelJson: (String) -> String?,
        cache: HashMap<String, ResolvedModel?>,
    ): ResolvedModel? {
        cache[id]?.let { return it }
        if (cache.containsKey(id)) return null

        val text = modelJson(id)
        if (text == null) {
            cache[id] = null
            return null
        }
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (json == null) {
            cache[id] = null
            return null
        }

        val parentTextures = json["parent"]?.jsonPrimitive?.contentOrNullOrEmpty()
            ?.removePrefix("minecraft:")
            ?.let { loadModel(it, modelJson, cache) }
        // Значение текстуры бывает строкой (`"block/oak_planks"`) или объектом с полями
        // (`{"sprite": "block/glass", "force_translucent": true}`) — так в 26.x помечают
        // прозрачные блоки. Без второй ветки стекло оставалось без модели.
        val ownTextures = buildMap<String, String> {
            (json["textures"] as? JsonObject)?.forEach { (key, value) ->
                textureValue(value)?.let { put(key, it) }
            }
        }
        val textures = parentTextures?.textures.orEmpty() + ownTextures
        val elements = (json["elements"] as? JsonArray) ?: parentTextures?.elements

        val model = ResolvedModel(textures, elements)
        cache[id] = model
        return model
    }

    /** Имя текстуры из строки или из объекта `{"sprite": ...}`. */
    private fun textureValue(value: kotlinx.serialization.json.JsonElement?): String? = when (value) {
        is JsonPrimitive -> if (value.isString) value.content else null
        is JsonObject -> (value["sprite"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        else -> null
    }

    /**
     * Текстура грани: сначала по направлению из элементов модели (первая же грань задаёт
     * вид), потом по именам переменных, если элементов нет (так устроены простые кубы).
     */
    private fun faceTexture(
        model: ResolvedModel,
        direction: String,
        fallbacks: List<String>,
        layerOf: (String) -> Int?,
    ): Int? {
        for (element in model.elements.orEmpty()) {
            val face = (element as? JsonObject)?.get("faces") as? JsonObject ?: continue
            val textureRef = textureValue((face[direction] as? JsonObject)?.get("texture")) ?: continue
            layerOf(resolveRef(textureRef, model.textures))?.let { return it }
        }
        for (name in fallbacks) {
            val reference = model.textures[name] ?: continue
            layerOf(resolveRef(reference, model.textures))?.let { return it }
        }
        return null
    }

    /** `#all` → значение переменной `all`, и так далее по цепочке. */
    private fun resolveRef(reference: String, textures: Map<String, String>): String {
        var value = reference
        var guard = 0
        while (value.startsWith("#") && guard++ < 8) {
            value = textures[value.substring(1)] ?: return value
        }
        return value
            .removePrefix("minecraft:")
            .removePrefix("block/")
    }
}
