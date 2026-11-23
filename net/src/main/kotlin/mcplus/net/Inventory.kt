package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader

/**
 * What the player carries: the 46 menu slots, the selected hotbar slot and the cursor stack.
 *
 * The server sends the whole menu once at join (`Container Set Content`, 0x12) and then single
 * slots (`Container Set Slot` 0x14, `Set Player Inventory` 0x6E) whenever something changes.
 * Each stack is `count, item id, component patch`.
 *
 * The patch is a list of components, each with its own codec, so an arbitrary patch cannot be
 * skipped without knowing every codec. A plain stack of blocks has an **empty** patch and is
 * read exactly; a stack with components is recorded as [UNKNOWN_ITEM] instead of being guessed,
 * which costs a name but never corrupts the packets that follow (the packet length is known).
 *
 * Slots are stored the way the wire numbers them — **menu slots** — because that is what
 * `Container Set Content`/`Slot` and the click packet use. `Set Player Inventory` and the held
 * slot use the inventory's own numbering, so those are converted on the way in. Mixing the two
 * is silent: items still arrive, they just land 36 slots away from the hand.
 */
class Inventory(
    private val items: ItemTable = ItemTable.EMPTY,
    private val stats: ItemStats = ItemStats.EMPTY,
) {

    data class Stack(
        val itemId: Int,
        val count: Int,
        val damage: Int = 0,
        /** Имя из компонента `custom_name`: переименованный предмет. */
        val customName: String? = null,
        /** Зачарования: пары «номер зачарования, уровень» (реестр зачарований встроен). */
        val enchantments: List<Pair<Int, Int>> = emptyList(),
        /** Редкость: 0 — обычная, 1 — необычная, 2 — редкая, 3 — эпическая. */
        val rarity: Int = 0,
        /** Цвет кожи: компонент `dyed_color` (0 — не покрашено). */
        val dyedColor: Int = 0,
        /** Строки описания из компонента `lore`. */
        val lore: List<String> = emptyList(),
        /** Предел стопки из компонента `max_stack_size` (0 — обычный для предмета). */
        val maxStackSize: Int = 0,
        /** `enchantment_glint_override`: блеск включают или выключают принудительно. */
        val glintOverride: Boolean? = null,
        /** Отделка брони: номера материала и узора из компонента `trim`. */
        val trimMaterial: Int = -1,
        val trimPattern: Int = -1,
        /** Сколько зарядов в арбалете (компонент `charged_projectiles`). */
        val chargedCount: Int = 0,
        /** Номер зелья из реестра и его цвет (`potion_contents`). */
        val potionId: Int = -1,
        val potionColor: Int = 0,
        /** Фейерверк: время взлёта и число зарядов (`fireworks`). */
        val fireworksFlight: Int = 0,
        val fireworkStars: Int = 0,
        /** Сколько предметов в мешке (`bundle_contents`). */
        val bundleCount: Int = 0,
        /** Сколько предметов внутри (`container`): шалкер, сундук в предмете. */
        val containerCount: Int = 0,
        /** Еда (`food`): сколько голода даёт, сколько насыщения и можно ли есть сытым. */
        val foodNutrition: Int = 0,
        val foodSaturation: Float = 0f,
        val foodAlwaysEat: Boolean = false,
        /** Цвет основы (`base_color`): номер красителя, −1 — не задан. */
        val baseColor: Int = -1,
        /** Звук нотного блока (`note_block_sound`), если задан. */
        val noteSound: String? = null,
        /** Сила дурного зелья (`ominous_bottle_amplifier`), −1 — не задана. */
        val ominousAmplifier: Int = -1,
        /** Эффекты подозрительного рагу (`suspicious_stew_effects`): номер и длительность. */
        val stewEffects: List<Pair<Int, Int>> = emptyList(),
        /** Инструмент козьего рога (`instrument`): номер из реестра, −1 — не задан. */
        val instrumentId: Int = -1,
    ) {
        val isEmpty: Boolean get() = count <= 0

        /** False when the stack is there but its item id could not be read (opaque components). */
        val isKnown: Boolean get() = itemId > 0
    }

    /** Menu slots 0..45. 0 = crafting result, 1..4 grid, 5..8 armor, 9..35 main, 36..44 hotbar, 45 offhand. */
    private val slots = java.util.concurrent.ConcurrentHashMap<Int, Stack>()

    /** Selected hotbar slot, 0..8. */
    @Volatile
    var selected: Int = 0
        private set

    /**
     * Menu state id from the last sync. The click packet echoes it; a stale value is not fatal —
     * the server then answers with the whole menu instead of just the changed slots.
     */
    @Volatile
    var stateId: Int = 0
        private set

    /** What is held on the mouse cursor while a container is open (menu slot -1). */
    @Volatile
    var carried: Stack? = null
        private set

    fun stackIn(inventorySlot: Int): Stack? = slots[menuSlotOfInventory(inventorySlot)]

    /** Any menu slot, including crafting and armor. */
    fun menuSlot(menuSlot: Int): Stack? = slots[menuSlot]

    /** The stack on the cursor, if any. */
    fun carriedOrNull(): Stack? = carried

    /** The item in the selected hotbar slot (menu slot 36 + selected). */
    fun held(): Stack? = slots[HOTBAR_MENU_START + selected]

    /** Name of the held item, or null when the hand is empty (or the stack has components). */
    fun heldName(): String? = nameOf(held())

    /**
     * Имя для показа игроку: переименованный предмет называется своим именем.
     *
     * В 26.3 компоненты приходят патчем к стеку; из них читаются износ, `custom_name` и
     * зачарования. Остальные компоненты незнакомы по длине — такой стек помечается
     * неизвестным, как и раньше.
     */
    fun displayNameOf(stack: Stack?): String? =
        stack?.customName?.takeIf { it.isNotBlank() } ?: nameOf(stack)

    fun nameOf(stack: Stack?): String? =
        stack?.takeIf { it.isKnown }?.let { items.nameOf(it.itemId) }

    /** Имя предмета по номеру: нужно там, где стопки нет — например, у станции рецепта. */
    fun nameById(itemId: Int): String? = if (itemId > 0) items.nameOf(itemId) else null

    /** Filled inventory slots in slot order; handy for the console and for tests. */
    fun contents(): List<Pair<Int, Stack>> =
        slots.entries
            .filter { !it.value.isEmpty }
            .mapNotNull { (menuSlot, stack) -> inventorySlotOfMenu(menuSlot)?.let { it to stack } }
            .sortedBy { it.first }

    /**
     * Стопки, из которых берутся ингредиенты крафта: основной инвентарь и хотбар.
     *
     * Это меню-слоты 9..44 — броня, сетка крафта и рука в счёт не идут. Когда открыт контейнер,
     * слоты игрока приходят в [OpenContainer], и считать нужно по нему — см. Session.
     */
    fun storedStacks(): List<Stack?> = (MAIN_MENU_START..HOTBAR_MENU_END).map { slots[it] }

    /**
     * Надетое: шлем, нагрудник, штаны, ботинки — в этом порядке.
     *
     * Слоты меню 5..8; броня приходит теми же пакетами, что и остальной инвентарь.
     */
    fun armour(): List<Stack> = ARMOUR_MENU_SLOTS.mapNotNull { menuSlot ->
        slots[menuSlot]?.takeIf { !it.isEmpty }
    }

    /** Одна часть брони по порядку: 0 шлем, 1 нагрудник, 2 штаны, 3 ботинки. */
    fun armourStack(index: Int): Stack? =
        slots[ARMOUR_MENU_SLOTS.first + index]?.takeIf { !it.isEmpty }

    /** Сколько очков брони даёт этот предмет (0, если это не броня или таблицы нет). */
    fun armourOf(stack: Stack?): Int =
        stack?.takeIf { it.isKnown }?.let { stats.armourOf(it.itemId) } ?: 0

    /**
     * Сколько очков брони на игроке.
     *
     * Очки берутся из таблицы предметов (`ItemStats`): на проводе у стека только id, а
     * `minecraft:armor` — это компонент предмета по умолчанию, а не часть стека.
     */
    fun armourPoints(): Int = ARMOUR_MENU_SLOTS.sumOf { armourOf(slots[it]) }

    /** Прочность надетого: сколько у предмета всего (для будущих полосок). */
    fun armourMaxDamage(): Int = ARMOUR_MENU_SLOTS.sumOf { menuSlot ->
        val stack = slots[menuSlot]?.takeIf { it.isKnown } ?: return@sumOf 0
        stats.of(stack.itemId)?.maxDamage ?: 0
    }

    fun select(slot: Int) {
        if (slot in 0..8) selected = slot
    }

    /** Menu slot for a hotbar index, e.g. what to click to take the held item. */
    fun hotbarMenuSlot(index: Int): Int = HOTBAR_MENU_START + index.coerceIn(0, 8)

    /**
     * Consumes the packets this class understands.
     *
     * @return true when the packet was an inventory packet, so the caller must not pass it on.
     */
    fun onPacket(packetId: Int, body: ByteArray): Boolean {
        when (packetId) {
            Protocol777.Play.CONTAINER_SET_SLOT -> {
                // container id, state id, slot (short), stack; slot -1 is the cursor.
                val reader = ByteReader(body)
                val container = reader.readVarInt()
                stateId = reader.readVarInt()
                val menuSlot = reader.readShort().toInt()
                val stack = readOrMarkUnreadable(reader)
                if (container != PLAYER_CONTAINER) return true
                if (menuSlot < 0) carried = stack?.takeIf { !it.isEmpty } else put(menuSlot, stack)
                return true
            }

            Protocol777.Play.SET_PLAYER_INVENTORY -> {
                // This one addresses the player's own inventory directly (0 = first hotbar slot).
                val reader = ByteReader(body)
                val inventorySlot = reader.readVarInt()
                put(menuSlotOfInventory(inventorySlot), readOrMarkUnreadable(reader))
                return true
            }

            Protocol777.Play.SET_CURSOR_ITEM -> {
                // The cursor stack has a packet of its own (`set_cursor_item`), it is not a slot.
                carried = readOrMarkUnreadable(ByteReader(body))?.takeIf { !it.isEmpty }
                return true
            }

            Protocol777.Play.SET_HELD_SLOT -> {
                val reader = ByteReader(body)
                select(reader.readVarInt())
                return true
            }

            Protocol777.Play.CONTAINER_SET_CONTENT -> {
                // The whole menu: 46 slots in a row. Parsing stops at the first stack with
                // components, because its payload length is unknown; the packet is then dropped
                // as a whole, which is safe (packets are length-delimited) and rare.
                runCatching {
                    val reader = ByteReader(body)
                    val container = reader.readVarInt()
                    stateId = reader.readVarInt()
                    if (container != PLAYER_CONTAINER) return true
                    val count = reader.readVarInt()
                    repeat(count) { menuSlot -> put(menuSlot, readStack(reader)) }
                    // The cursor stack travels at the end of the same packet, not as a slot.
                    carried = readStack(reader)?.takeIf { !it.isEmpty }
                }
                return true
            }

            else -> return false
        }
    }

    private fun put(menuSlot: Int, stack: Stack?) {
        if (stack == null || stack.isEmpty) slots.remove(menuSlot) else slots[menuSlot] = stack
    }

    /**
     * Reads one stack: `count` (0 = empty), item id, then the component patch.
     *
     * The patch starts with two counts (added, removed). Added components have per-codec bodies
     * of unknown length, so a non-empty "added" list makes the stack unreadable: it is reported
     * as [UNKNOWN_ITEM] with the count we did read, and the rest of the packet is abandoned.
     */
    private fun readStack(reader: ByteReader): Stack? = readMenuStack(reader)

    /** A single-slot packet can afford this: the count is known, the name is not. */
    private fun readOrMarkUnreadable(reader: ByteReader): Stack? = readMenuStackOrUnknown(reader)

    /**
     * The player inventory menu lists crafting first, so the hotbar is 36..44 there, while the
     * inventory itself numbers it 0..8. `Container Set Content`/`Slot` use the menu numbering.
     */
    private fun inventorySlotOfMenu(menuSlot: Int): Int? = when (menuSlot) {
        0 -> null                          // crafting result
        in 1..4 -> null                    // crafting grid
        in HOTBAR_MENU_START..44 -> menuSlot - HOTBAR_MENU_START
        in 9..35 -> menuSlot               // main inventory (same number)
        in 5..8 -> 36 + (menuSlot - 5)     // armor: inventory 36..39
        45 -> 40                           // offhand
        else -> null
    }

    private fun menuSlotOfInventory(inventorySlot: Int): Int = when (inventorySlot) {
        in 0..8 -> HOTBAR_MENU_START + inventorySlot
        in 9..35 -> inventorySlot
        in 36..39 -> 5 + (inventorySlot - 36)
        40 -> 45
        else -> inventorySlot
    }

    private companion object {
        /** The player's own inventory is container 0; chests and the like are not supported. */
        const val PLAYER_CONTAINER = 0

        /** Item id used when a stack carries components we cannot parse. */
        const val UNKNOWN_ITEM = -1

        const val HOTBAR_MENU_START = 36

        /** Последний слот хотбара. */
        const val HOTBAR_MENU_END = 44

        /** Первый слот основного инвентаря. */
        const val MAIN_MENU_START = 9

        /** Меню-слоты надетой брони: шлем, нагрудник, штаны, ботинки. */
        val ARMOUR_MENU_SLOTS = 5..8
    }
}

/** Один стек в меню: count, item id, патч. Бросает [UnreadableStack] на незнакомых компонентах. */
internal fun readMenuStack(reader: ByteReader): Inventory.Stack? {
    val count = reader.readVarInt()
    if (count <= 0) return null
    val itemId = reader.readVarInt()

    val added = reader.readVarInt()
    val removed = reader.readVarInt()
    // Патч компонентов: износ, имя, описание, редкость и зачарования читаются, остальное
    // неизвестно по длине — такой стек помечается «неизвестным», но пакет не рвётся.
    var damage = 0
    var customName: String? = null
    val enchantments = ArrayList<Pair<Int, Int>>()
    var rarity = 0
    var dyedColor = 0
    var lore: List<String> = emptyList()
    var maxStackSize = 0
    var glintOverride: Boolean? = null
    var trimMaterial = -1
    var trimPattern = -1
    var chargedCount = 0
    var potionId = -1
    var potionColor = 0
    var fireworksFlight = 0
    var fireworkStars = 0
    var bundleCount = 0
    var containerCount = 0
    var foodNutrition = 0
    var foodSaturation = 0f
    var foodAlwaysEat = false
    var baseColor = -1
    var noteSound: String? = null
    var ominousAmplifier = -1
    var stewEffects: List<Pair<Int, Int>> = emptyList()
    var instrumentId = -1
    repeat(added) {
        val id = reader.readVarInt()
        when (ComponentTable.active.nameOf(id)) {
            "damage" -> damage = reader.readVarInt()
            // Размер прочности и цена ремонта — по одному числу.
            "max_damage", "repair_cost" -> reader.readVarInt()
            // `unbreakable` — компонент без полезной нагрузки.
            "unbreakable" -> Unit
            // Предел стопки и редкость — тоже по одному VarInt.
            "max_stack_size" -> maxStackSize = reader.readVarInt()
            "rarity" -> rarity = reader.readVarInt()
            // Цвет кожи — обычный int (0xRRGGBB).
            "dyed_color" -> dyedColor = reader.readInt()
            "enchantment_glint_override" -> glintOverride = reader.readBoolean()
            "custom_name", "item_name" -> customName = mcplus.net.componentText(
                mcplus.protocol.nbt.NbtReader.read(reader),
            )
            // Описание: количество строк и сами строки компонентами NBT.
            "lore" -> {
                val lines = reader.readVarInt()
                if (lines !in 0..MAX_LORE_LINES) throw UnreadableStack(count)
                lore = (0 until lines).map {
                    mcplus.net.componentText(mcplus.protocol.nbt.NbtReader.read(reader))
                }
            }
            "enchantments", "stored_enchantments" -> {
                val size = reader.readVarInt()
                if (size !in 0..MAX_ENCHANTMENTS) throw UnreadableStack(count)
                repeat(size) { enchantments += reader.readVarInt() to reader.readVarInt() }
            }

            // Компоненты без полезной нагрузки: планировщик крыла и «нематериальный снаряд».
            "glider", "intangible_projectile" -> Unit
            // `custom_data` — произвольный NBT датапака: он самодостаточен по длине,
            // поэтому его можно прочитать и выбросить, а не считать стек «неизвестным».
            "custom_data" -> mcplus.protocol.nbt.NbtReader.read(reader)
            // Узоры флага: список пар «узор, цвет», оба — номера реестров.
            "banner_patterns" -> {
                val layers = reader.readVarInt()
                if (layers !in 0..MAX_BANNER_LAYERS) throw UnreadableStack(count)
                repeat(layers) {
                    reader.readVarInt()
                    reader.readVarInt()
                }
            }
            // Одно число: минимальный заряд удара и множитель длительности зелья — float,
            // доплата торговцу, уровень зачаруемости и номер карты — VarInt.
            "minimum_attack_charge", "potion_duration_scale" -> reader.readFloat()
            "additional_trade_cost", "enchantable", "map_id" -> reader.readVarInt()
            // Ссылки на модель предмета и стиль подсказки — обычные строки.
            "item_model", "tooltip_style" -> reader.readString()
            // Отделка брони: материал и узор, оба — номера реестров.
            "trim" -> {
                trimMaterial = reader.readVarInt()
                trimPattern = reader.readVarInt()
            }            // Заряженный арбалет: список зарядов тем же форматом, что и обычный стек.
            "charged_projectiles" -> {
                val shots = reader.readVarInt()
                if (shots !in 0..MAX_PROJECTILES) throw UnreadableStack(count)
                repeat(shots) { skipTemplateStack(reader) }
                chargedCount = shots
            }

            // Зелье: недописанный вариант, свой цвет, свои эффекты и своё имя.
            // Свои эффекты встречаются редко, и их формат вложенный — такие зелья остаются
            // «неизвестными», зато обычные (с номером зелья из реестра) читаются точно.
            "potion_contents" -> {
                val hasPotion = reader.readBoolean()
                if (hasPotion) potionId = reader.readVarInt()
                val hasColor = reader.readBoolean()
                if (hasColor) potionColor = reader.readInt()
                val effects = reader.readVarInt()
                if (effects != 0) throw UnreadableStack(count)
                val hasName = reader.readBoolean()
                if (hasName) {
                    val text = mcplus.net.componentText(mcplus.protocol.nbt.NbtReader.read(reader))
                    if (text != null) customName = customName ?: text
                }
            }

            // Фейерверк: список взрывов (форма, цвета, шлейф и мерцание) и время взлёта.
            // Ракеты без этого компонента не бывает, поэтому без разбора предмет был «неизвестным».
            "fireworks" -> {
                val stars = reader.readVarInt()
                if (stars !in 0..MAX_FIREWORK_STARS) throw UnreadableStack(count)
                repeat(stars) { skipFireworkExplosion(reader) }
                fireworksFlight = reader.readVarInt()
                fireworkStars = stars
            }
            "firework_explosion" -> skipFireworkExplosion(reader)

            // Модель предмета из датапака: четыре списка — числа, флаги, строки, цвета.
            // На серверах этим перекрашивают предметы, поэтому без разбора они были
            // «неизвестными» и теряли имя.
            "custom_model_data" -> {
                val floats = reader.readVarInt()
                if (floats !in 0..MAX_CUSTOM_MODEL_ENTRIES) throw UnreadableStack(count)
                repeat(floats) { reader.readFloat() }
                val flags = reader.readVarInt()
                if (flags !in 0..MAX_CUSTOM_MODEL_ENTRIES) throw UnreadableStack(count)
                repeat(flags) { reader.readBoolean() }
                val strings = reader.readVarInt()
                if (strings !in 0..MAX_CUSTOM_MODEL_ENTRIES) throw UnreadableStack(count)
                repeat(strings) { reader.readString() }
                val colors = reader.readVarInt()
                if (colors !in 0..MAX_CUSTOM_MODEL_ENTRIES) throw UnreadableStack(count)
                repeat(colors) { reader.readInt() }
            }

            // Мешок: список вложенных стеков тем же форматом, что и заряды арбалета.
            "bundle_contents" -> {
                val items = reader.readVarInt()
                if (items !in 0..MAX_PROJECTILES) throw UnreadableStack(count)
                repeat(items) { skipTemplateStack(reader) }
                bundleCount = items
            }

            // Содержимое в предмете (шалкер, сундук): у каждого слота признак «есть» и стопка.
            "container" -> {
                val items = reader.readVarInt()
                if (items !in 0..MAX_CONTAINER_ITEMS) throw UnreadableStack(count)
                repeat(items) { if (reader.readBoolean()) skipTemplateStack(reader) }
                containerCount = items
            }

            // Еда: голод, насыщение и «можно есть сытым» — три поля подряд.
            "food" -> {
                foodNutrition = reader.readVarInt()
                foodSaturation = reader.readFloat()
                foodAlwaysEat = reader.readBoolean()
            }

            // Одно число или строка: краситель основы, сила дурного зелья и звук нотного блока.
            "base_color" -> baseColor = reader.readVarInt()
            "ominous_bottle_amplifier" -> ominousAmplifier = reader.readVarInt()
            "note_block_sound" -> noteSound = reader.readString()

            // Козий рог: номер инструмента из реестра.
            "instrument" -> instrumentId = reader.readVarInt()

            // Подозрительное рагу: список «эффект, длительность».
            "suspicious_stew_effects" -> {
                val effects = reader.readVarInt()
                if (effects !in 0..MAX_STEW_EFFECTS) throw UnreadableStack(count)
                stewEffects = List(effects) { reader.readVarInt() to reader.readVarInt() }
            }

            else -> throw UnreadableStack(count)
        }
    }
    repeat(removed) { reader.readVarInt() }

    return Inventory.Stack(
        itemId = itemId,
        count = count,
        damage = damage,
        customName = customName,
        enchantments = enchantments,
        rarity = rarity,
        dyedColor = dyedColor,
        lore = lore,
        maxStackSize = maxStackSize,
        glintOverride = glintOverride,
        trimMaterial = trimMaterial,
        trimPattern = trimPattern,
        chargedCount = chargedCount,
        potionId = potionId,
        potionColor = potionColor,
        fireworksFlight = fireworksFlight,
        fireworkStars = fireworkStars,
        bundleCount = bundleCount,
        containerCount = containerCount,
        foodNutrition = foodNutrition,
        foodSaturation = foodSaturation,
        foodAlwaysEat = foodAlwaysEat,
        baseColor = baseColor,
        noteSound = noteSound,
        ominousAmplifier = ominousAmplifier,
        stewEffects = stewEffects,
        instrumentId = instrumentId,
    )
}

/** То же, но нечитаемый стек превращается в «неизвестный предмет» с количеством. */
internal fun readMenuStackOrUnknown(reader: ByteReader): Inventory.Stack? =
    try {
        readMenuStack(reader)
    } catch (e: UnreadableStack) {
        Inventory.Stack(-1, e.count)
    }

internal class UnreadableStack(val count: Int) : RuntimeException()

/** Больше зачарований на одном предмете в игре не бывает. */
private const val MAX_ENCHANTMENTS = 64

/** Зарядов в арбалете или предметов в мешке больше этого не бывает. */
private const val MAX_PROJECTILES = 64

/** Больше зарядов в ракете в игре не бывает. */
private const val MAX_FIREWORK_STARS = 8

/** Списки в `custom_model_data` короткие: это имена моделей и цвета, а не массивы. */
private const val MAX_CUSTOM_MODEL_ENTRIES = 64

/**
 * Пропустить описание одного взрыва фейерверка.
 *
 * Порядок полей взят из `FireworkExplosion.STREAM_CODEC`: форма, цвета, цвета затухания,
 * шлейф, мерцание. Списки цветов — длина VarInt и сами цвета.
 */
private fun skipFireworkExplosion(reader: ByteReader) {
    reader.readVarInt()
    val colors = reader.readVarInt()
    if (colors !in 0..MAX_FIREWORK_STARS * 2) throw UnreadableStack(colors)
    repeat(colors) { reader.readInt() }
    val fade = reader.readVarInt()
    if (fade !in 0..MAX_FIREWORK_STARS * 2) throw UnreadableStack(fade)
    repeat(fade) { reader.readInt() }
    reader.readBoolean()
    reader.readBoolean()
}

/**
 * Пропустить вложенную стопку (`ItemStackTemplate`): предмет, количество и патч компонентов.
 *
 * Порядок полей — как у шаблона стопки, а не у обычного стека: сначала номер предмета.
 * Если во вложенной стопке есть компоненты, длину тела мы не знаем — такой предмет
 * считается непрочитанным, но пакет от этого не рвётся.
 */
private fun skipTemplateStack(reader: ByteReader) {
    reader.readVarInt()               // предмет
    val count = reader.readVarInt()   // количество
    if (count <= 0) return
    val added = reader.readVarInt()
    if (added != 0) throw UnreadableStack(count)
    if (reader.readVarInt() != 0) throw UnreadableStack(count)
}

/** Больше слотов в предмете-контейнере в игре не бывает. */
private const val MAX_CONTAINER_ITEMS = 256

/** Эффектов в рагу в игре не больше восьми. */
private const val MAX_STEW_EFFECTS = 8

/** Предел строк описания: в игре `ItemLore.MAX_LINES`. */
private const val MAX_LORE_LINES = 256

/** Узоров на флаге в игре не больше двадцати. */
private const val MAX_BANNER_LAYERS = 32
