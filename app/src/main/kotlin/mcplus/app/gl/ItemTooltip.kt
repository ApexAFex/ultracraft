package mcplus.app.gl

import mcplus.net.Inventory

/**
 * Строки подсказки под предметом: имя своим цветом, метка зачарования и описание.
 *
 * Цвета — ванильные: обычный предмет белый, необычный жёлтый, редкий голубой, эпический
 * сиреневый. У зачарованного предмета в игре имя светится, а у нас имени не хватает —
 * поэтому рядом стоит знак `✦`.
 */
object ItemTooltip {

    /** Цвет имени по редкости: 0 — обычная, 1 — необычная, 2 — редкая, 3 — эпическая. */
    fun rarityColor(rarity: Int): Int = when (rarity) {
        1 -> 0xFFFF55
        2 -> 0x55FFFF
        3 -> 0xFF55FF
        else -> 0xFFFFFF
    }

    /**
     * Строки подсказки для стека.
     *
     * @param name имя предмета без количества
     * @param maxDamage предел прочности (0 — предмет не изнашивается)
     * @param enchantmentName имя зачарования по номеру из реестра (null — покажем номер)
     */
    fun lines(
        stack: Inventory.Stack,
        name: String,
        maxDamage: Int = 0,
        enchantmentName: (Int) -> String? = { null },
        trimPatternName: (Int) -> String? = { null },
        trimMaterialName: (Int) -> String? = { null },
        /** Имя эффекта по номеру из реестра: нужно для подозрительного рагу. */
        effectName: (Int) -> String? = { null },
        /** Имя инструмента козьего рога; без реестра показываем номер. */
        instrumentName: (Int) -> String = { "№$it" },
    ): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>(5)
        val title = buildString {
            append(name)
            if (stack.count > 1) append(" ×${stack.count}")
            if (stack.enchantments.isNotEmpty() || stack.glintOverride == true) append(" ✦")
        }
        result += title to rarityColor(stack.rarity)
        // Прочность отдельной строкой: в заголовке она ломала имя, а так видно и остаток,
        // и предел.
        if (maxDamage > 0 && stack.damage > 0) {
            val left = (maxDamage - stack.damage).coerceAtLeast(0)
            val color = when {
                left.toFloat() / maxDamage > 0.5f -> 0x7FD07F
                left.toFloat() / maxDamage > 0.25f -> 0xD8C860
                else -> 0xD07070
            }
            result += "Прочность: $left из $maxDamage" to color
        }
        // Описание идёт серым, как в игре; строки уже разобраны из компонента `lore`.
        for (line in stack.lore) result += line to 0xAAAAAA
        if (stack.enchantments.isNotEmpty()) {
            // «Острота III» вместо «Зачарования: 3»: имя берётся из реестра зачарований,
            // который сервер присылает при входе.
            val text = stack.enchantments.joinToString(", ") { (id, level) ->
                val name = enchantmentName(id) ?: "зачарование $id"
                "$name ${roman(level)}"
            }
            result += text to 0x8090C0
        }
        // Отделка брони: «Отделка: coast (золото)». Без реестра показываем номера.
        if (stack.trimPattern >= 0 || stack.trimMaterial >= 0) {
            val pattern = trimPatternName(stack.trimPattern) ?: "узор ${stack.trimPattern}"
            val material = trimMaterialName(stack.trimMaterial) ?: "материал ${stack.trimMaterial}"
            result += "Отделка: $pattern ($material)" to 0xA0A0D0
        }
        // Заряженный арбалет: сколько зарядов внутри.
        if (stack.chargedCount > 0) {
            result += "Заряды: ${stack.chargedCount}" to 0xC8C8A0
        }
        // Фейерверк: сколько зарядов и как высоко взлетит (1 — ниже, 2 — выше).
        if (stack.fireworksFlight > 0 || stack.fireworkStars > 0) {
            result += "Ракета: ${stack.fireworkStars} зар., взлёт ${stack.fireworksFlight}" to 0xC8C8A0
        }
        // Мешок: сколько предметов внутри.
        if (stack.bundleCount > 0) {
            result += "В мешке: ${stack.bundleCount}" to 0xC8C8A0
        }
        // Предмет-контейнер: шалкер или сундук в кармане.
        if (stack.containerCount > 0) {
            result += "Внутри: ${stack.containerCount}" to 0xC8C8A0
        }
        // Еда: голод и насыщение; «можно есть сытым» — важная подсказка, в игре она есть.
        if (stack.foodNutrition > 0) {
            val saturation = String.format("%.1f", stack.foodSaturation).replace('.', ',')
            val extra = if (stack.foodAlwaysEat) ", можно есть сытым" else ""
            result += "Еда: ${stack.foodNutrition} (+$saturation)$extra" to 0xC8C8A0
        }
        // Дурное зелье: сила эффекта.
        if (stack.ominousAmplifier >= 0) {
            result += "Сила дурного зелья: ${stack.ominousAmplifier + 1}" to 0xB090C0
        }
        // Подозрительное рагу: какие эффекты и на сколько.
        if (stack.stewEffects.isNotEmpty()) {
            val text = stack.stewEffects.joinToString(", ") { (id, ticks) ->
                "${effectName(id) ?: "эффект $id"} ${ticks / 20} с"
            }
            result += "Эффекты: $text" to 0xB090C0
        }
        // Козий рог: какой инструмент играет.
        if (stack.instrumentId >= 0) {
            result += "Инструмент: ${instrumentName(stack.instrumentId)}" to 0xC8C8A0
        }
        return result
    }

    /** Римская запись уровня зачарования, как в игре: 1 → I, 5 → V, 10 → X. */
    fun roman(level: Int): String {
        if (level <= 0) return level.toString()
        if (level > 3999) return level.toString()
        var left = level
        val builder = StringBuilder()
        for ((value, symbol) in ROMAN) {
            while (left >= value) {
                builder.append(symbol)
                left -= value
            }
        }
        return builder.toString()
    }

    private val ROMAN = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
}
