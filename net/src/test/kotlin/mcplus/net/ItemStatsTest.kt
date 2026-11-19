package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Броня: очки берутся из таблицы предметов, а не из пакета.
 *
 * В 26.x `minecraft:armor` — это компонент предмета по умолчанию, поэтому на проводе его нет:
 * стек несёт только id. Проверяется и таблица, и то, что очки считаются лишь по слотам брони.
 */
class ItemStatsTest {

    private val items = ItemTable(listOf("air", "iron_helmet", "iron_chestplate"))
    private val stats = ItemStats(
        listOf(
            null,
            ItemStat(armour = 2, toughness = 0, maxDamage = 165),
            ItemStat(armour = 6, toughness = 0, maxDamage = 240),
        ),
    )

    private fun setInventorySlot(inventorySlot: Int, itemId: Int): ByteArray =
        ByteWriter()
            .writeVarInt(inventorySlot)
            .writeVarInt(1)
            .writeVarInt(itemId)
            .writeVarInt(0)   // added components
            .writeVarInt(0)   // removed components
            .toByteArray()

    private fun inventoryWithHelmetAndChest(): Inventory {
        val inventory = Inventory(items, stats)
        // 36..39 — слоты инвентаря для брони: шлем, нагрудник, штаны, ботинки.
        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, setInventorySlot(36, 1))
        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, setInventorySlot(37, 2))
        return inventory
    }

    @Test
    fun `armour points are summed over the armour slots`() {
        assertEquals(8, inventoryWithHelmetAndChest().armourPoints())
    }

    @Test
    fun `pieces are visible in helmet to boots order`() {
        val inventory = inventoryWithHelmetAndChest()
        assertEquals("iron_helmet", inventory.nameOf(inventory.armourStack(0)))
        assertEquals("iron_chestplate", inventory.nameOf(inventory.armourStack(1)))
        assertNull(inventory.armourStack(2))
        assertEquals(2, inventory.armour().size)
    }

    @Test
    fun `items outside the armour slots do not count`() {
        val inventory = Inventory(items, stats)
        // 9 — первый слот основного инвентаря, брони там нет.
        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, setInventorySlot(9, 2))
        assertEquals(0, inventory.armourPoints())
    }

    @Test
    fun `unknown or missing items give no points`() {
        val inventory = Inventory(items, ItemStats.EMPTY)
        inventory.onPacket(Protocol777.Play.SET_PLAYER_INVENTORY, setInventorySlot(36, 1))
        assertEquals(0, inventory.armourPoints())
        assertEquals(0, inventory.armourMaxDamage())
    }

    @Test
    fun `max damage is summed for later durability bars`() {
        assertEquals(165 + 240, inventoryWithHelmetAndChest().armourMaxDamage())
    }

    @Test
    fun `table parses armour toughness and durability`() {
        val file = Files.createTempFile("item-stats", ".tsv").toFile()
        try {
            file.writeText("1081\t6\t2\t240\n971\t0\t0\t25\n")
            val loaded = ItemStats.load(file)

            assertEquals(ItemStat(6, 2, 240), loaded.of(1081))
            assertEquals(6, loaded.armourOf(1081))
            assertEquals(0, loaded.armourOf(971))
            assertEquals(0, loaded.armourOf(9999))
        } finally {
            file.delete()
        }
    }
}
