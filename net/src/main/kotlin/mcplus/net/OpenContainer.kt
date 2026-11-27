package mcplus.net

import mcplus.protocol.ByteReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Открытый контейнер: сундук, верстак, печь — всё, что сервер открыл через `Open Screen`.
 *
 * Слоты адресуются номерами **меню**: сначала слоты контейнера, затем 36 слотов игрока
 * (27 инвентаря и 9 хотбара). Точное число слотов контейнера приходит в `Container Set
 * Content`; до этого оно оценивается по типу меню. Номера меню и их раскладку задаёт
 * сервер, поэтому клиент их не вычисляет — он только запоминает, что пришло.
 */
class OpenContainer(
    val id: Int,
    val type: String,
    val title: String,
    slotCount: Int,
) {

    private val slots = ConcurrentHashMap<Int, Inventory.Stack>()

    /** Сколько всего слотов в меню; уточняется из `Container Set Content`. */
    @Volatile
    var slotCount: Int = slotCount
        private set

    @Volatile
    var stateId: Int = 0
        private set

    /** Предмет на курсоре — он приходит и внутри меню, и отдельным пакетом. */
    @Volatile
    var carried: Inventory.Stack? = null
        private set

    /** Данные меню (прогресс печи и подобное): номер свойства → значение. */
    private val data = ConcurrentHashMap<Int, Int>()

    /** Первый слот игрока: последние 36 слотов меню. */
    val playerStart: Int get() = (slotCount - PLAYER_SLOTS).coerceAtLeast(0)

    fun slot(index: Int): Inventory.Stack? = slots[index]

    fun carriedOrNull(): Inventory.Stack? = carried

    fun dataValue(property: Int): Int? = data[property]

    fun contents(): List<Pair<Int, Inventory.Stack>> =
        slots.entries.filter { !it.value.isEmpty }.sortedBy { it.key }.map { it.key to it.value }

    /**
     * Стопки игрока внутри открытого меню: последние 36 слотов.
     *
     * Нужны крафту: пока открыт верстак, инвентарь приезжает именно сюда, а не в [Inventory].
     */
    fun playerStacks(): List<Inventory.Stack?> =
        (playerStart until slotCount).map { slots[it] }

    internal fun put(index: Int, stack: Inventory.Stack?) {
        if (stack == null || stack.isEmpty) slots.remove(index) else slots[index] = stack
    }

    internal fun setState(stateId: Int) {
        this.stateId = stateId
    }

    internal fun setSlotCount(count: Int) {
        slotCount = count
    }

    internal fun putCarried(stack: Inventory.Stack?) {
        carried = stack?.takeIf { !it.isEmpty }
    }

    internal fun putData(property: Int, value: Int) {
        data[property] = value
    }

    /**
     * Сколько слотов контейнера до инвентаря игрока — по типу меню.
     *
     * Для сундуков это 9·N, у верстака — результат и сетка 3×3 (10), у печей — вход, топливо
     * и выход (3). Остальные меню пока считаются «всё до инвентаря игрока».
     */
    val containerSlots: Int get() = MenuLayout.containerSlots(type, slotCount)

    companion object {
        const val PLAYER_SLOTS = 36
    }
}

/** Раскладка меню по имени типа: нужна экрану и тестам. */
object MenuLayout {

    fun slotCount(type: String): Int = when {
        type.startsWith("generic_9x") -> {
            val rows = type.removePrefix("generic_9x").toIntOrNull() ?: 1
            9 * rows + OpenContainer.PLAYER_SLOTS
        }

        // Раздатчик и выбрасыватель приходят сервером как `generic_3x3` (проверено на живом
        // сервере: «Контейнер: generic_3x3, «Dispenser», слотов 45»), а не своим именем.
        type == "generic_3x3" -> 9 + OpenContainer.PLAYER_SLOTS

        // Кафедра: один слот под книгу и инвентарь игрока.
        type == "crafting" -> 10 + OpenContainer.PLAYER_SLOTS
        type == "lectern" -> 1 + OpenContainer.PLAYER_SLOTS
        type in FURNACES -> 3 + OpenContainer.PLAYER_SLOTS
        type == "inventory" -> 46

        // Остальные станции: число слотов контейнера плюс инвентарь игрока.
        // Раньше все они попадали в «неизвестный» случай и показывали неверную сетку.
        type in TOOL_SLOTS -> TOOL_SLOTS.getValue(type) + OpenContainer.PLAYER_SLOTS

        // Новые блоки с фиксированной сеткой.
        type == "hopper" -> 5 + OpenContainer.PLAYER_SLOTS
        type == "crafter_3x3" -> 9 + OpenContainer.PLAYER_SLOTS
        type == "shulker_box" -> 27 + OpenContainer.PLAYER_SLOTS

        else -> 0
    }

    /**
     * Слоты контейнера у станций: сколько предметов кладёт игрок, столько и слотов.
     *
     * **Числа сняты с живого сервера** (`container --at x,y,z` открывает блок и печатает
     * «слотов N»), а не взяты на глаз: наковальня 39, кузня 40, ткацкий станок 40, точило 39,
     * камнерез **38** (в нём два слота — вход и результат), картография 39, варочная 41,
     * кафедра торговли 39, чары 38, маяк 37, воронка 41, шалкер 63.
     */
    val TOOL_SLOTS: Map<String, Int> = mapOf(
        "enchantment" to 2,   // предмет и лазурит
        "beacon" to 1,        // только предмет
        "anvil" to 3,
        "smithing" to 4,
        "grindstone" to 3,
        "loom" to 4,
        "stonecutter" to 2,   // вход и результат
        "cartography_table" to 3,
        "merchant" to 3,
        "brewing_stand" to 5,
    )

    /**
     * Слоты контейнера до инвентаря игрока. Если тип неизвестен, всё меню до последних
     * 36 слотов считается контейнером — так сундук нестандартного размера всё равно виден.
     */
    fun containerSlots(type: String, slotCount: Int): Int = when {
        type.startsWith("generic_9x") -> {
            val rows = type.removePrefix("generic_9x").toIntOrNull() ?: 1
            9 * rows
        }

        type == "crafting" -> 10
        type == "lectern" -> 1
        type in FURNACES -> 3
        type == "generic_3x3" -> 9
        type in TOOL_SLOTS -> TOOL_SLOTS.getValue(type)
        type == "hopper" -> 5
        type == "crafter_3x3" -> 9
        type == "shulker_box" -> 27
        else -> (slotCount - OpenContainer.PLAYER_SLOTS).coerceAtLeast(0)
    }

    fun isChest(type: String): Boolean = type.startsWith("generic_9x")

    val FURNACES = setOf("furnace", "blast_furnace", "smoker")
}

/**
 * Разбор `Open Screen` (0x3C): id окна, тип меню и заголовок.
 *
 * Заголовок — компонент в NBT, как в чате; его текст нужен только для подписи окна.
 */
internal fun readOpenScreen(body: ByteArray, menus: MenuTable): OpenContainer {
    val reader = ByteReader(body)
    val id = reader.readVarInt()
    val typeId = reader.readVarInt()
    val title = componentText(mcplus.protocol.nbt.NbtReader.read(reader)).trim()
    val type = menus.nameOf(typeId)
    return OpenContainer(id, type, title, MenuLayout.slotCount(type))
}

/** Заполняет открытое меню из `Container Set Content` (0x12). */
internal fun applyContainerContent(container: OpenContainer, body: ByteArray) {
    val reader = ByteReader(body)
    val id = reader.readVarInt()
    if (id != container.id) return
    container.setState(reader.readVarInt())
    val count = reader.readVarInt()
    if (count !in 0..MAX_MENU_SLOTS) return
    container.setSlotCount(count)
    repeat(count) { index ->
        container.put(index, readMenuStackOrUnknown(reader))
    }
    container.putCarried(readMenuStackOrUnknown(reader))
}

/** Один слот меню из `Container Set Slot` (0x14). */
internal fun applyContainerSlot(container: OpenContainer, body: ByteArray) {
    val reader = ByteReader(body)
    val id = reader.readVarInt()
    if (id != container.id) return
    container.setState(reader.readVarInt())
    val slot = reader.readShort().toInt()
    container.put(slot, readMenuStackOrUnknown(reader))
}

/** `Container Set Data` (0x13): номер свойства и значение. */
internal fun applyContainerData(container: OpenContainer, body: ByteArray) {
    val reader = ByteReader(body)
    if (reader.readVarInt() != container.id) return
    val property = reader.readShort().toInt()
    val value = reader.readShort().toInt()
    container.putData(property, value)
}

private const val MAX_MENU_SLOTS = 512
