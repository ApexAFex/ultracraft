package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The inventory is where "what do I hold" lives, and the server numbers its slots two different
 * ways: menu slots in `Container Set Content`/`Container Set Slot` (hotbar is 36..44 there) and
 * inventory slots in `Set Player Inventory` (hotbar is 0..8). Mixing them up is silent — items
 * still arrive, they just land 36 slots away from the hand, which is exactly what happened.
 */
class InventoryTest {

    private val items = ItemTable(listOf("air", "stone", "granite", "oak_planks"))

    private fun stack(count: Int, itemId: Int, added: Int = 0, removed: Int = 0): ByteWriter =
        ByteWriter()
            .writeVarInt(count)
            .apply { if (count > 0) writeVarInt(itemId) }
            .apply { if (count > 0) writeVarInt(added).writeVarInt(removed) }

    @Test
    fun `content packet addresses menu slots where the hotbar starts at 36`() {
        val inventory = Inventory(items)
        // 46 menu slots: stone in menu slot 36, which is the first hotbar slot.
        val full = ByteWriter().writeVarInt(0).writeVarInt(7).writeVarInt(46)
        for (slot in 0 until 46) {
            if (slot == 36) {
                full.writeBytes(stack(8, 1).toByteArray())
            } else {
                full.writeVarInt(0)
            }
        }

        inventory.onPacket(Protocol777.Play.CONTAINER_SET_CONTENT, full.toByteArray())

        assertEquals(Inventory.Stack(1, 8), inventory.stackIn(0))
        assertEquals("stone", inventory.heldName())
        assertNull(inventory.stackIn(36))
    }

    @Test
    fun `single slot packet uses menu slots too`() {
        val inventory = Inventory(items)
        val body = ByteWriter()
            .writeVarInt(0)
            .writeVarInt(3)
            .writeShort(37)      // second hotbar slot
            .writeBytes(stack(4, 3).toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.CONTAINER_SET_SLOT, body)

        assertEquals(Inventory.Stack(3, 4), inventory.stackIn(1))
    }

    @Test
    fun `set player inventory packet uses inventory slots`() {
        val inventory = Inventory(items)
        val body = ByteWriter()
            .writeVarInt(2)      // inventory slot 2 = third hotbar slot
            .writeBytes(stack(1, 2).toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

        assertEquals(Inventory.Stack(2, 1), inventory.stackIn(2))
    }

    @Test
    fun `held slot packet picks the hand`() {
        val inventory = Inventory(items)
        val body = ByteWriter().writeVarInt(1).toByteArray()

        inventory.onPacket(Protocol777.Play.SET_HELD_SLOT, body)

        assertEquals(1, inventory.selected)
    }

    @Test
    fun `empty stack clears the slot`() {
        val inventory = Inventory(items)
        inventory.onPacket(
            Protocol777.Play.SET_PLAYER_INVENTORY,
            ByteWriter().writeVarInt(0).writeBytes(stack(3, 1).toByteArray()).toByteArray(),
        )
        assertEquals(1, inventory.contents().size)

        inventory.onPacket(
            Protocol777.Play.SET_PLAYER_INVENTORY,
            ByteWriter().writeVarInt(0).writeVarInt(0).toByteArray(),
        )

        assertEquals(0, inventory.contents().size)
        assertNull(inventory.heldName())
    }

    @Test
    fun `a stack with components keeps its count but loses its name`() {
        val inventory = Inventory(items)
        // added = 1: the payload of that component cannot be skipped, so the item id is unknown.
        val body = ByteWriter()
            .writeVarInt(0)
            .writeBytes(stack(5, 1, added = 1).also { it.writeVarInt(0) }.toByteArray())
            .toByteArray()

        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

        assertEquals(5, inventory.stackIn(0)?.count)
        assertNull(inventory.heldName())
    }

    @Test
    fun `a damaged tool keeps its name and damage`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)   // один добавленный компонент
                .writeVarInt(0)   // удалённых нет
                .writeVarInt(3)   // damage (как в таблице игры)
                .writeVarInt(37)  // износ 37
            val body = ByteWriter().writeVarInt(0).writeBytes(stack.toByteArray()).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals(37, inventory.stackIn(0)?.damage)
            assertNotNull(inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `enchantments and a custom name are read from the component patch`() {
        val previous = ComponentTable.active
        // Индексы как в data/components-777.tsv: damage, custom_name, attribute_modifiers.
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val name = "Клинок".toByteArray(Charsets.UTF_8)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(3)   // три добавленных компонента
                .writeVarInt(0)   // удалённых нет
                .writeVarInt(3)   // damage
                .writeVarInt(12)
                .writeVarInt(6)   // custom_name
                .writeByte(0x08)  // NBT: строка
                .writeShort(name.size)
                .writeBytes(name)
                .writeVarInt(16)  // attribute_modifiers — незнакомый компонент
            val body = ByteWriter().writeVarInt(0).writeBytes(stack.toByteArray()).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            // Незнакомый компонент в патче: стек неизвестен, но количество сохраняется.
            assertNull(inventory.heldName())
            assertEquals(1, inventory.stackIn(0)?.count)
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `rarity, dye, lore and the stack limit are read too`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            fun nbtString(text: String): ByteArray {
                val bytes = text.toByteArray(Charsets.UTF_8)
                return ByteWriter()
                    .writeByte(0x08)
                    .writeShort(bytes.size)
                    .writeBytes(bytes)
                    .toByteArray()
            }
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(5)    // пять компонентов
                .writeVarInt(0)
                .writeVarInt(1)    // max_stack_size = 16
                .writeVarInt(16)
                .writeVarInt(12)   // rarity = 2 (редкий)
                .writeVarInt(2)
                .writeVarInt(47)   // dyed_color
                .writeInt(0x3366CC)
                .writeVarInt(21)   // enchantment_glint_override = false
                .writeBoolean(false)
                .writeVarInt(11)   // lore: одна строка
                .writeVarInt(1)
                .writeBytes(nbtString("Строка описания"))
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(16, parsed.maxStackSize)
            assertEquals(2, parsed.rarity)
            assertEquals(0x3366CC, parsed.dyedColor)
            assertEquals(false, parsed.glintOverride)
            assertEquals(listOf("Строка описания"), parsed.lore)
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `payload-free and single-value components are read`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(5)
                .writeVarInt(0)
                .writeVarInt(34)   // glider — без полезной нагрузки
                .writeVarInt(42)   // additional_trade_cost = 7
                .writeVarInt(7)
                .writeVarInt(10)   // item_model = "stone"
                .writeString("minecraft:stone")
                .writeVarInt(7)    // minimum_attack_charge = 0.5
                .writeFloat(0.5f)
                .writeVarInt(26)   // use_cooldown — всё ещё незнакомый
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            // Последний компонент незнаком, поэтому стек «неизвестен», но количество на месте:
            // значит, предыдущие компоненты прочитаны ровно и пакет не разъехался.
            assertNull(inventory.heldName())
            assertEquals(1, inventory.stackIn(0)?.count)
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `a glider with only known components stays a known stack`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(3)
                .writeVarInt(0)
                .writeVarInt(34)   // glider
                .writeVarInt(1)    // max_stack_size = 1
                .writeVarInt(1)
                .writeVarInt(47)   // dyed_color
                .writeInt(0xFF0000)
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(1, parsed.maxStackSize)
            assertEquals(0xFF0000, parsed.dyedColor)
            assertEquals("stone", inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `custom data is skipped by its own nbt length`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            // custom_data = NBT-словарь {"a": 7}, за ним обычный компонент damage.
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(2)
                .writeVarInt(0)
                .writeVarInt(0)      // custom_data
                .writeByte(0x0A)     // NBT: compound (в сетевом NBT имени корня нет)
                .writeByte(0x01)     // байт (TAG_Byte = 1)
                .writeShort(1).writeBytes("a".toByteArray(Charsets.UTF_8))
                .writeByte(7)
                .writeByte(0x00)     // конец compound
                .writeVarInt(3)      // damage = 5
                .writeVarInt(5)
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(5, parsed.damage, "после custom_data пакет читается дальше")
            assertEquals("stone", inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `banner patterns are read as pairs of registry ids`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(74)   // banner_patterns
                .writeVarInt(2)    // два узора
                .writeVarInt(4).writeVarInt(1)
                .writeVarInt(7).writeVarInt(14)
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals("stone", inventory.heldName(), "флаг с узорами остаётся известным")
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `the trim component is stored as two registry ids`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("trim")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeVarInt(3)    // материал: золото
                .writeVarInt(7)    // узор: coast
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(3, parsed.trimMaterial)
            assertEquals(7, parsed.trimPattern)
            assertEquals("stone", inventory.heldName(), "броня с отделкой остаётся известной")
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `charged projectiles are counted and skipped`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("charged_projectiles")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeVarInt(2)    // два заряда
                .writeVarInt(1).writeVarInt(1).writeVarInt(0).writeVarInt(0)   // 1 камень
                .writeVarInt(1).writeVarInt(2).writeVarInt(0).writeVarInt(0)   // 1 гранит
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals(2, inventory.stackIn(0)?.chargedCount)
            assertEquals("stone", inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    /** Номер компонента по имени из настоящей таблицы. */
    private fun dataComponentId(name: String): Int {
        val table = componentTable()
        for (id in 0 until 256) {
            if (table.nameOf(id) == name) return id
        }
        error("В таблице нет компонента $name")
    }

    @Test
    fun `a potion keeps its registry id and colour`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("potion_contents")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeBoolean(true).writeVarInt(12)     // зелье: исцеление
                .writeBoolean(true).writeInt(0x385DC6)  // свой цвет
                .writeVarInt(0)                         // своих эффектов нет
                .writeBoolean(false)                    // своего имени нет
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(12, parsed.potionId)
            assertEquals(0x385DC6, parsed.potionColor)
            assertEquals("stone", inventory.heldName(), "зелье остаётся известным предметом")
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `a firework rocket keeps its flight and stars`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("fireworks")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeVarInt(1)                 // один взрыв
                .writeVarInt(2)                 // форма: звезда
                .writeVarInt(2).writeInt(0xFF0000).writeInt(0x00FF00)  // цвета
                .writeVarInt(0)                 // без затухания
                .writeBoolean(true)             // шлейф
                .writeBoolean(false)            // без мерцания
                .writeVarInt(2)                 // взлёт 2
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertEquals(1, parsed.fireworkStars)
            assertEquals(2, parsed.fireworksFlight)
            assertEquals("stone", inventory.heldName(), "ракета остаётся известным предметом")
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `custom model data is four lists and does not corrupt the stack`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("custom_model_data")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeVarInt(1).writeFloat(1.5f)                  // числа
                .writeVarInt(2).writeBoolean(true).writeBoolean(false)  // флаги
                .writeVarInt(1).writeString("minecraft:custom")    // строки
                .writeVarInt(1).writeInt(0x123456)                 // цвета
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals("stone", inventory.heldName(), "предмет с моделью датапака остаётся известным")
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `a bundle counts its contents and keeps its name`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val id = dataComponentId("bundle_contents")
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(0)
                .writeVarInt(id)
                .writeVarInt(2)    // два предмета в мешке
                .writeVarInt(1).writeVarInt(1).writeVarInt(0).writeVarInt(0)
                .writeVarInt(3).writeVarInt(2).writeVarInt(0).writeVarInt(0)
                .toByteArray()
            val body = ByteWriter().writeVarInt(0).writeBytes(stack).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals(2, inventory.stackIn(0)?.bundleCount)
            assertEquals("stone", inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `a renamed enchanted sword is a known stack`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val name = "Клинок".toByteArray(Charsets.UTF_8)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(3)
                .writeVarInt(0)
                .writeVarInt(13)  // enchantments: 1 зачарование, id 5, уровень 3
                .writeVarInt(1)
                .writeVarInt(5)
                .writeVarInt(3)
                .writeVarInt(6)   // custom_name
                .writeByte(0x08)
                .writeShort(name.size)
                .writeBytes(name)
                .writeVarInt(3)   // damage
                .writeVarInt(9)
            val body = ByteWriter().writeVarInt(0).writeBytes(stack.toByteArray()).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            val parsed = inventory.stackIn(0)!!
            assertNotNull(inventory.heldName())
            assertEquals("Клинок", parsed.customName)
            assertEquals(listOf(5 to 3), parsed.enchantments)
            assertEquals(9, parsed.damage)
            assertEquals("Клинок", inventory.displayNameOf(parsed))
        } finally {
            ComponentTable.active = previous
        }
    }

    @Test
    fun `an unbreakable item is skipped without payload`() {
        val previous = ComponentTable.active
        ComponentTable.active = componentTable()
        try {
            val inventory = Inventory(items)
            val stack = ByteWriter()
                .writeVarInt(1)
                .writeVarInt(1)
                .writeVarInt(2)
                .writeVarInt(0)
                .writeVarInt(4)   // unbreakable: без данных
                .writeVarInt(3)   // damage: число
                .writeVarInt(77)
            val body = ByteWriter().writeVarInt(0).writeBytes(stack.toByteArray()).toByteArray()
            inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, body)

            assertEquals(77, inventory.stackIn(0)?.damage)
            assertNotNull(inventory.heldName())
        } finally {
            ComponentTable.active = previous
        }
    }

    /**
     * Таблица компонентов — настоящая, из `data/components-777.tsv`.
     *
     * Раньше здесь был рукописный список, и номера в тестах разъезжались с игрой: например,
     * у `banner_patterns` номер 74, а не 51. Теперь тест берёт ту же таблицу, что и клиент.
     */
    private fun componentTable(): ComponentTable {
        // У `net`-тестов рабочий каталог — корень модуля, у `app`-тестов — корень проекта.
        val file = listOf("data/components-777.tsv", "../data/components-777.tsv")
            .map { java.io.File(it) }
            .firstOrNull { it.isFile }
            ?: error("Нет таблицы компонентов: искали data/components-777.tsv")
        return ComponentTable.load(file)
    }

    @Test
    fun `the cursor arrives at the end of the whole menu packet`() {
        val inventory = Inventory(items)
        val body = ByteWriter().writeVarInt(0).writeVarInt(4).writeVarInt(46)
        for (slot in 0 until 46) {
            if (slot == 0) {
                fullStack(body, 4, 3)   // crafting result
            } else {
                body.writeVarInt(0)
            }
        }
        fullStack(body, 2, 1)           // cursor: two stone

        inventory.onPacket(Protocol777.Play.CONTAINER_SET_CONTENT, body.toByteArray())

        assertEquals(3, inventory.menuSlot(0)?.itemId)
        assertEquals(Inventory.Stack(1, 2), inventory.carriedOrNull())
        assertEquals(4, inventory.stateId)
    }

    @Test
    fun `the cursor can come in its own packet`() {
        val inventory = Inventory(items)
        val body = ByteWriter()
        body.writeVarInt(2).writeVarInt(3).writeVarInt(0).writeVarInt(0)

        inventory.onPacket(Protocol777.Play.SET_CURSOR_ITEM, body.toByteArray())

        assertEquals(Inventory.Stack(3, 2), inventory.carriedOrNull())

        inventory.onPacket(
            Protocol777.Play.SET_CURSOR_ITEM,
            ByteWriter().writeVarInt(0).toByteArray(),
        )
        assertNull(inventory.carriedOrNull())
    }

    private fun fullStack(writer: ByteWriter, count: Int, itemId: Int) {
        writer.writeVarInt(count).writeVarInt(itemId).writeVarInt(0).writeVarInt(0)
    }
}
