package mcplus.app.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Разбор `sounds.json`: событие → файлы `.ogg`.
 *
 * В игре звук запрашивается событием (`block.stone.break`), а не файлом; событие может
 * ссылаться на другое событие (`type: "event"`), поэтому ссылки разворачиваются здесь, а не
 * в момент воспроизведения. Список вариантов нужен, чтобы один и тот же шаг не звучал
 * одинаково.
 */
object SoundCatalog {

    fun parse(json: String): Map<String, List<String>> {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyMap()
        val raw = HashMap<String, List<JsonObject?>>()
        for ((event, value) in root) {
            val sounds = runCatching { value.jsonObject["sounds"]?.jsonArray }.getOrNull() ?: continue
            raw[event] = sounds.map { element ->
                when (element) {
                    is JsonObject -> element
                    is JsonPrimitive -> JsonObject(mapOf("name" to element))
                    else -> null
                }
            }
        }

        val result = HashMap<String, List<String>>()
        for (event in raw.keys) {
            val files = LinkedHashSet<String>()
            resolve(event, raw, files, 0)
            if (files.isNotEmpty()) result[event] = files.toList()
        }
        return result
    }

    private fun resolve(
        event: String,
        raw: Map<String, List<JsonObject?>>,
        out: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        for (sound in raw[event].orEmpty()) {
            val name = sound?.get("name")?.let { (it as? JsonPrimitive)?.content } ?: continue
            val type = sound["type"]?.let { (it as? JsonPrimitive)?.content }
            if (type == "event") {
                resolve(name, raw, out, depth + 1)
            } else {
                // В `name` путь без расширения: `block/stone/break1` → файл в sounds/.
                out += "sounds/$name.ogg"
            }
        }
    }

    /**
     * Ключи субтитров: событие → `subtitles.…`.
     *
     * `sounds.json` держит подпись рядом с событием, поэтому её не надо придумывать.
     * У событий-ссылок (`type: event`) своей подписи обычно нет — берём подпись цели.
     */
    fun subtitles(json: String): Map<String, String> {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return emptyMap()
        val direct = HashMap<String, String>()
        val references = HashMap<String, String>()
        for ((event, value) in root) {
            val obj = runCatching { value.jsonObject }.getOrNull() ?: continue
            obj["subtitle"]?.let { subtitle ->
                val key = runCatching { subtitle.jsonPrimitive.content }.getOrNull()
                if (!key.isNullOrBlank()) direct[event] = key
            }
            val first = obj["sounds"]?.jsonArray?.firstOrNull() as? JsonObject
            val type = first?.get("type")?.let { (it as? JsonPrimitive)?.content }
            val target = first?.get("name")?.let { (it as? JsonPrimitive)?.content }
            if (type == "event" && target != null) references[event] = target
        }
        for ((event, target) in references) {
            if (event !in direct) direct[target]?.let { direct[event] = it }
        }
        return direct
    }

    private const val MAX_DEPTH = 5
}
