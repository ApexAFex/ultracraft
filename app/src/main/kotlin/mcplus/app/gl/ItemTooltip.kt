package mcplus.app.gl

import mcplus.net.AttributeMod
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
        /** Имя атрибута по номеру: из таблицы `data/attributes-777.tsv`. */
        attributeName: (Int) -> String? = { mcplus.net.AttributeTable.active.nameOf(it) },
        /** Имя песни по номеру из реестра пластинок. */
        songName: (Int) -> String? = { null },
        /** Имя вида существа по номеру: нужно для ведра с рыбой и улья с пчёлами. */
        entityName: (Int) -> String? = { null },
    ): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>(5)
        val title = buildString {
            append(name)
            if (stack.count > 1) append(" ×${stack.count}")
            if (stack.enchantments.isNotEmpty() || stack.glintOverride == true) append(" ✦")
        }
        result += title to rarityColor(stack.rarity)
        // `tooltip_display` с флагом «спрятать всё»: остаётся только имя.
        if (stack.hideTooltip) return result
        // Прочность отдельной строкой: в заголовке она ломала имя, а так видно и остаток,
        // и предел. Компонент `max_damage` важнее таблицы предметов: датапак мог его поменять.
        val durabilityLimit = if (stack.maxDamage > 0) stack.maxDamage else maxDamage
        if (durabilityLimit > 0 && stack.damage > 0) {
            val left = (durabilityLimit - stack.damage).coerceAtLeast(0)
            val color = when {
                left.toFloat() / durabilityLimit > 0.5f -> 0x7FD07F
                left.toFloat() / durabilityLimit > 0.25f -> 0xD8C860
                else -> 0xD07070
            }
            result += "Прочность: $left из $durabilityLimit" to color
        }
        // «Неразрушимый» и цена ремонта: в игре это отдельные строки подсказки.
        if (stack.unbreakable) result += "Неразрушимый" to 0x55FFFF
        if (stack.repairCost > 0) result += "Цена ремонта: ${stack.repairCost}" to 0xA0A0A0
        // Покрашенная кожа: сам цвет, иначе непонятно, чем предмет отличается от обычного.
        if (stack.dyedColorSet) {
            result += "Цвет: #%06X".format(stack.dyedColor and 0xFFFFFF) to 0xA0A0A0
        }
        // Описание идёт серым, как в игре; строки уже разобраны из компонента `lore`.
        if (!isHidden(stack, "lore")) {
            for (line in stack.lore) result += line to 0xAAAAAA
        }
        // Модификаторы атрибутов: «+2 к урону», «+20 % к скорости». Свой текст сервера
        // показываем как есть, спрятанные модификаторы не показываем вовсе.
        for (mod in stack.attributeMods) {
            if (mod.hidden) continue
            val text = mod.display ?: attributeLine(mod, attributeName)
            result += text to 0x4F6FA8
        }
        if (stack.enchantments.isNotEmpty() && !isHidden(stack, "enchantments")) {
            // «Острота III» вместо «Зачарования: 3»: имя берётся из реестра зачарований,
            // который сервер присылает при входе.
            val text = stack.enchantments.joinToString(", ") { (id, level) ->
                val name = enchantmentName(id) ?: "зачарование $id"
                "$name ${roman(level)}"
            }
            result += text to 0x8090C0
        }
        // Отделка брони: «Отделка: coast (золото)». Без реестра показываем номера.
        if ((stack.trimPattern >= 0 || stack.trimMaterial >= 0) && !isHidden(stack, "trim")) {
            val pattern = trimPatternName(stack.trimPattern) ?: "узор ${stack.trimPattern}"
            val material = trimMaterialName(stack.trimMaterial) ?: "материал ${stack.trimMaterial}"
            result += "Отделка: $pattern ($material)" to 0xA0A0D0
        }
        // Голова с профилем: чья она. Ник приходит компонентом `profile`.
        stack.profileName?.let { result += "Голова: $it" to 0xC8C8A0 }
        // Пластинка: какая песня записана.
        if (stack.songId >= 0) {
            result += "Пластинка: ${songName(stack.songId) ?: "№${stack.songId}"}" to 0xC8C8A0
        }
        // Книга: заголовок, автор и число страниц.
        stack.bookTitle?.let { title ->
            val author = stack.bookAuthor?.let { ", автор $it" } ?: ""
            result += "Книга: «$title»$author, страниц ${stack.bookPages}" to 0xC8C8A0
        }
        // Режим приключений: на что можно ставить и что ломать.
        if (stack.canPlaceOn.isNotEmpty()) {
            result += "Ставится на: ${blockList(stack.canPlaceOn)}" to 0xA0A0A0
        }
        if (stack.canBreak.isNotEmpty()) {
            result += "Ломает: ${blockList(stack.canBreak)}" to 0xA0A0A0
        }
        // Чем чинится предмет: тег или перечень.
        val repair = stack.repairTag ?: stack.repairIds.takeIf { it.isNotEmpty() }?.joinToString(", ") {
            "предмет $it"
        }
        if (repair != null) result += "Чинится: $repair" to 0xA0A0A0
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
        // Ведро с существом: вид из таблицы существ (рыба, аксолотль, лягушка).
        if (stack.entityTypeId >= 0) {
            result += "Существо: ${entityName(stack.entityTypeId) ?: "№${stack.entityTypeId}"}" to 0xC8C8A0
        }
        // Улей-предмет с пчёлами.
        if (stack.beeCount > 0) {
            result += "Пчёл внутри: ${stack.beeCount}" to 0xC8C8A0
        }
        return result
    }

    /** Строка модификатора атрибута: «+2 к урону», «+20% к скорости (в руке)». */
    private fun attributeLine(mod: AttributeMod, name: (Int) -> String?): String {
        val key = name(mod.attributeId)
        val label = ATTRIBUTE_LABELS[key]
            ?: key?.replace('_', ' ')
            ?: "атрибут ${mod.attributeId}"
        val value = if (mod.operation == 0) signed(mod.amount) else "${signed(mod.amount * 100)}%"
        val slot = SLOT_LABELS[mod.slot]
        return if (slot == null) "$value $label" else "$value $label ($slot)"
    }

    /** «+2» или «−0,5»: знак всегда, дробная часть через запятую, как в остальном интерфейсе. */
    private fun signed(value: Double): String {
        val text = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value).trimEnd('0').trimEnd('.').replace('.', ',')
        }
        return if (value >= 0) "+$text" else text
    }

    /** Компонент спрятан через `tooltip_display`: его строку не показываем. */
    private fun isHidden(stack: Inventory.Stack, component: String): Boolean {
        if (stack.hiddenComponents.isEmpty()) return false
        val id = mcplus.net.ComponentTable.active.idOf(component)
        return id >= 0 && id in stack.hiddenComponents
    }

    /** Список блоков допуска: тег как «тег logs», номер — именем блока из реестра. */
    private fun blockList(entries: List<String>): String = entries.joinToString(", ") { entry ->
        when {
            entry.startsWith("…") -> entry
            entry.startsWith("#") -> {
                val id = entry.substring(1).toIntOrNull()
                val name = id?.let { mcplus.net.BlockRegistryTable.active.nameOf(it) }
                name ?: "блок №${entry.substring(1)}"
            }
            else -> "тег ${entry.removePrefix("minecraft:")}"
        }
    }

    /**
     * Подписи атрибутов по-русски: в подсказке «+2 к урону» читается лучше, чем
     * «+2 attack_damage». Незнакомый атрибут показывается своим именем без подчёркиваний.
     */
    private val ATTRIBUTE_LABELS = mapOf(
        "attack_damage" to "к урону",
        "attack_speed" to "к скорости атаки",
        "attack_knockback" to "к отбрасыванию",
        "armor" to "к броне",
        "armor_toughness" to "к твёрдости брони",
        "max_health" to "к здоровью",
        "max_absorption" to "к поглощению урона",
        "movement_speed" to "к скорости",
        "movement_efficiency" to "к эффективности движения",
        "sneaking_speed" to "к скорости ползком",
        "flying_speed" to "к скорости полёта",
        "swimming_speed" to "к скорости плавания",
        "water_movement_efficiency" to "к плаванию",
        "knockback_resistance" to "к стойкости к отбрасыванию",
        "explosion_knockback_resistance" to "к стойкости к взрывам",
        "luck" to "к удаче",
        "jump_strength" to "к прыжку",
        "gravity" to "к гравитации",
        "scale" to "к размеру",
        "step_height" to "к высоте шага",
        "safe_fall_distance" to "к безопасному падению",
        "fall_damage_multiplier" to "к урону от падения",
        "oxygen_bonus" to "к запасу воздуха",
        "burning_time" to "к времени горения",
        "mining_efficiency" to "к скорости копания",
        "block_break_speed" to "к скорости ломания",
        "submerged_mining_speed" to "к копанию под водой",
        "sweeping_damage_ratio" to "к урону размаха",
        "block_interaction_range" to "к дальности действия",
        "entity_interaction_range" to "к дальности до существ",
        "camera_distance" to "к дальности камеры",
        "friction_modifier" to "к трению",
        "bounciness" to "к упругости",
        "air_drag_modifier" to "к сопротивлению воздуха",
        "follow_range" to "к дальности следования",
        "tempt_range" to "к дальности приманки",
        "name_tag_distance" to "к дальности имени",
        "below_name_distance" to "к дальности подписи",
        "spawn_reinforcements" to "к подкреплению",
        "waypoint_transmit_range" to "к передаче метки",
        "waypoint_receive_range" to "к приёму метки",
    )

    /** Слоты `EquipmentSlotGroup`: 0 — где угодно, поэтому подписи нет. */
    private val SLOT_LABELS = mapOf(
        1 to "в руке",
        2 to "в левой руке",
        3 to "в руках",
        4 to "на обуви",
        5 to "на штанах",
        6 to "на нагруднике",
        7 to "на шлеме",
        8 to "на броне",
        9 to "на теле",
        10 to "в седле",
    )

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
