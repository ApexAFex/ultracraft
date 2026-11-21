package mcplus.net

import java.io.File

/**
 * Климаты и профессии жителей по номерам реестров.
 *
 * `Villager Data` присылает не имена, а номера в реестрах `minecraft:villager_type` и
 * `minecraft:villager_profession`. Номера задаёт сервер, поэтому таблица собирается из его
 * отчёта `registries.json` (`tools/build-villager-table.js`), а не вписана руками: так
 * порядок реестра не разъедется с игрой при обновлении.
 */
class VillagerTable(
    private val types: List<String>,
    private val professions: List<String>,
) {

    /** Климат (`plains`, `desert`, …) или пустая строка, если номер неизвестен. */
    fun typeName(id: Int): String = types.getOrNull(id).orEmpty()

    /** Профессия (`farmer`, `none`, …) или пустая строка. */
    fun professionName(id: Int): String = professions.getOrNull(id).orEmpty()

    val isEmpty: Boolean get() = types.isEmpty() && professions.isEmpty()

    companion object {
        val EMPTY = VillagerTable(emptyList(), emptyList())

        /**
         * Читает TSV вида `type<TAB>номер<TAB>имя` и `profession<TAB>номер<TAB>имя`.
         * Строки с незнакомым видом и битыми номерами пропускаются: таблица — данные игры,
         * и падать из-за одной строки клиент не должен.
         */
        fun load(file: File): VillagerTable {
            val types = ArrayList<String>()
            val professions = ArrayList<String>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = line.split('\t')
                val kind = parts.getOrNull(0)?.trim() ?: return@forEachLine
                val id = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEachLine
                val name = parts.getOrNull(2)?.trim().orEmpty()
                val target = when (kind) {
                    "type" -> types
                    "profession" -> professions
                    else -> return@forEachLine
                }
                while (target.size <= id) target.add("")
                target[id] = name
            }
            return VillagerTable(types, professions)
        }
    }
}
