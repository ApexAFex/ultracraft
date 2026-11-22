package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Merchant Offers`: список сделок жителя.
 *
 * Тело собрано вручную по `MerchantOffer.writeToStream`: цена A, результат, флаг второй цены
 * и сама цена B, «нет в наличии», использования, лимит, опыт, наценка, множитель, спрос.
 * Перепутанный порядок здесь ломает весь экран торговли, поэтому проверяется каждое поле.
 */
class TradesTest {

    private fun cost(item: Int, count: Int) = ByteWriter()
        .writeVarInt(item)
        .writeVarInt(count)
        .writeVarInt(0)          // предикат компонентов пуст

    private fun offer(
        costAItem: Int = 1,
        costACount: Int = 3,
        resultItem: Int = 2,
        resultCount: Int = 1,
        costB: Pair<Int, Int>? = null,
        outOfStock: Boolean = false,
        uses: Int = 0,
        maxUses: Int = 12,
        xp: Int = 2,
        specialPriceDiff: Int = 0,
        priceMultiplier: Float = 0.05f,
        demand: Int = 0,
    ): ByteWriter {
        val writer = ByteWriter()
        costAItem.let { writer.writeBytes(cost(it, costACount).toByteArray()) }
        // Результат: количество, номер предмета, пустой патч компонентов.
        writer.writeVarInt(resultCount)
        writer.writeVarInt(resultItem)
        writer.writeVarInt(0)     // добавить компонентов
        writer.writeVarInt(0)     // убрать компонентов
        if (costB == null) {
            writer.writeBoolean(false)
        } else {
            writer.writeBoolean(true)
            writer.writeBytes(cost(costB.first, costB.second).toByteArray())
        }
        writer.writeBoolean(outOfStock)
        writer.writeInt(uses)
        writer.writeInt(maxUses)
        writer.writeInt(xp)
        writer.writeInt(specialPriceDiff)
        writer.writeFloat(priceMultiplier)
        writer.writeInt(demand)
        return writer
    }

    private fun packet(
        offers: List<ByteWriter>,
        containerId: Int = 7,
        level: Int = 2,
        xp: Int = 20,
        showProgress: Boolean = true,
        canRestock: Boolean = true,
    ): ByteArray {
        val writer = ByteWriter().writeVarInt(containerId).writeVarInt(offers.size)
        for (offer in offers) writer.writeBytes(offer.toByteArray())
        writer.writeVarInt(level)
        writer.writeVarInt(xp)
        writer.writeBoolean(showProgress)
        writer.writeBoolean(canRestock)
        return writer.toByteArray()
    }

    @Test
    fun `one offer is read field by field`() {
        val trades = Trades.parse(packet(listOf(offer())))

        assertEquals(7, trades?.containerId)
        assertEquals(2, trades?.villagerLevel)
        assertEquals(20, trades?.villagerXp)
        assertEquals(true, trades?.showProgress)
        assertEquals(true, trades?.canRestock)

        val parsed = trades?.offers?.single()
        assertEquals(1, parsed?.costA?.itemId)
        assertEquals(3, parsed?.costA?.count)
        assertEquals(null, parsed?.costB)
        assertEquals(2, parsed?.result?.itemId)
        assertEquals(1, parsed?.result?.count)
        assertEquals(0, parsed?.uses)
        assertEquals(12, parsed?.maxUses)
        assertEquals(2, parsed?.xp)
        assertEquals(0.05f, parsed?.priceMultiplier)
        assertEquals(0, parsed?.demand)
    }

    @Test
    fun `a second cost and the out of stock flag survive`() {
        val trades = Trades.parse(
            packet(listOf(offer(costB = 4 to 2, outOfStock = true, uses = 12))),
        )
        val parsed = trades?.offers?.single()

        assertEquals(4, parsed?.costB?.itemId)
        assertEquals(2, parsed?.costB?.count)
        assertEquals(true, parsed?.outOfStock)
        assertEquals(0, parsed?.remaining, "12 из 12 — торговец отдыхает")
    }

    @Test
    fun `several offers keep their order`() {
        val trades = Trades.parse(
            packet(
                listOf(
                    offer(costAItem = 1, resultItem = 10, resultCount = 4),
                    offer(costAItem = 5, resultItem = 11),
                    offer(costAItem = 6, resultItem = 12, maxUses = 1),
                ),
            ),
        )

        val offers = trades?.offers ?: error("пакет не разобрался")
        assertEquals(3, offers.size)
        assertEquals(listOf(10, 11, 12), offers.map { it.result?.itemId })
        assertEquals(listOf(4, 1, 1), offers.map { it.result?.count })
    }

    @Test
    fun `price grows with demand and never drops below one`() {
        // 10 предметов, спрос 3, множитель 0.05 → +1 (floor(1.5)).
        assertEquals(
            11,
            Trades.modifiedCost(Trades.Cost(1, 10), demand = 3, priceMultiplier = 0.05f, specialPriceDiff = 0),
        )
        // Наценка −20 не делает сделку бесплатной.
        assertEquals(
            1,
            Trades.modifiedCost(Trades.Cost(1, 2), demand = 0, priceMultiplier = 0.05f, specialPriceDiff = -20),
        )
        // Обычная цена без спроса и наценки — та, что в пакете.
        assertEquals(
            3,
            Trades.modifiedCost(Trades.Cost(1, 3), demand = 0, priceMultiplier = 0.05f, specialPriceDiff = 0),
        )
    }

    @Test
    fun `a line shows what you give and what you get`() {
        val items = mapOf(1 to "Изумруд", 2 to "Каменная кирка", 9 to "Хлеб")
        val trades = Trades.parse(
            packet(
                listOf(
                    offer(costAItem = 1, costACount = 3, resultItem = 2),
                    offer(costAItem = 9, costACount = 1, resultItem = 1, resultCount = 3),
                ),
            ),
        )

        assertEquals(
            listOf("Каменная кирка ← 3 Изумруд", "Изумруд ×3 ← 1 Хлеб"),
            trades?.lines { items[it] ?: "предмет#$it" },
        )
    }

    @Test
    fun `level names and progress follow the vanilla thresholds`() {
        assertEquals("Новичок", Trades.levelName(1))
        assertEquals("Мастер", Trades.levelName(5))
        assertEquals(5 to 10, Trades.progress(1, 5), "первый уровень — до 10 опыта")
        assertEquals(0 to 60, Trades.progress(2, 10), "второй — до 70")
        assertEquals(50 to 100, Trades.progress(4, 200), "четвёртый — до 250")
    }

    @Test
    fun `select trade is a single offer index`() {
        assertEquals(listOf(0x02), SelectTrade.packet(2).map { it.toInt() and 0xFF })
        assertEquals(0x34, Protocol777.Play.SELECT_TRADE)
        assertEquals(0x35, Protocol777.Play.MERCHANT_OFFERS)
        assertEquals(0x1A, Protocol777.Play.INTERACT)
    }

    @Test
    fun `a broken body gives no trades instead of half a list`() {
        val full = packet(listOf(offer(), offer()))
        assertNull(Trades.parse(full.copyOf(full.size - 3)), "обрезанный пакет — не список сделок")

        // Незнакомый компонент у цены: длину значения мы не знаем, поэтому пакет брошен.
        val withComponents = ByteWriter()
            .writeVarInt(7).writeVarInt(1)
            .writeVarInt(1).writeVarInt(2).writeVarInt(1) // цена с одним компонентом
        assertNull(Trades.parse(withComponents.toByteArray()))
    }

    @Test
    fun `scrolling stops at the first and last trade`() {
        // Сделок 10, видно 7: прокрутка идёт от 0 до 3.
        assertEquals(0, Trades.scrollAfter(0, -1, 7, 10), "выше первой строки не уехать")
        assertEquals(1, Trades.scrollAfter(0, 1, 7, 10))
        assertEquals(3, Trades.scrollAfter(3, 1, 7, 10), "ниже последней тоже")
        assertEquals(0, Trades.scrollAfter(0, 1, 7, 5), "если всё влезло — не листаем")
        assertEquals(0, Trades.scrollAfter(0, 1, 0, 5))
    }

    @Test
    fun `an empty list is valid`() {
        val trades = Trades.parse(packet(emptyList(), canRestock = false))
        assertEquals(0, trades?.offers?.size)
        assertEquals(false, trades?.canRestock)
        assertTrue(trades?.lines { "?" }?.isEmpty() == true)
    }
}
