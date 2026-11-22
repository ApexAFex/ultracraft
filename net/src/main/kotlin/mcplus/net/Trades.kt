package mcplus.net

import mcplus.net.proto.Protocol777
import mcplus.protocol.ByteReader
import mcplus.protocol.ByteWriter

/**
 * Товары жителя из `Merchant Offers` (`0x35`).
 *
 * Пакет несёт готовый список сделок: что торговец берёт, что даёт, сколько раз сделку уже
 * использовали и насколько поднялась цена. Без него экран жителя пуст: сами предметы лежат
 * в контейнере, но списка сделок в контейнере нет.
 *
 * Формат — поля `MerchantOffer.writeToStream` сервера, в его же порядке: цена A, результат,
 * необязательная цена B, «нет в наличии», использования, лимит, опыт, наценка, множитель
 * цены и спрос. Целые числа — обычные big-endian int, множитель — float, остальное VarInt.
 */
class Trades(
    val containerId: Int,
    val offers: List<Offer>,
    val villagerLevel: Int,
    val villagerXp: Int,
    val showProgress: Boolean,
    val canRestock: Boolean,
) {

    /** Цена одного вида предметов: номер предмета из реестра и количество. */
    data class Cost(val itemId: Int, val count: Int)

    /** Одна сделка: что платим (до двух стеков), что получаем и сколько раз уже брали. */
    data class Offer(
        val costA: Cost,
        val costB: Cost?,
        val result: Inventory.Stack?,
        val outOfStock: Boolean,
        val uses: Int,
        val maxUses: Int,
        val xp: Int,
        val specialPriceDiff: Int,
        val priceMultiplier: Float,
        val demand: Int,
    ) {
        /**
         * Сколько предметов просят за сделку *сейчас*: ванильная `getModifiedCostCount`.
         *
         * Спрос и наценка поднимают цену, но не ниже одного предмета — иначе сделка
         * превратилась бы в бесплатную.
         */
        val priceA: Int get() = modifiedCost(costA, demand, priceMultiplier, specialPriceDiff)

        val priceB: Int?
            get() = costB?.let { modifiedCost(it, demand, priceMultiplier, specialPriceDiff) }

        /** Сколько ещё раз можно взять сделку (0 — торговец отдыхает). */
        val remaining: Int get() = (maxUses - uses).coerceAtLeast(0)

        /** Название результата: «Изумруд ×3», «Каменная кирка» или «?». */
        fun resultName(itemName: (Int) -> String): String {
            val stack = result ?: return "?"
            val name = if (stack.itemId < 0) "?" else itemName(stack.itemId)
            return if (stack.count > 1) "$name ×${stack.count}" else name
        }

        /** Одна строка для списка сделок: «Каменная кирка ← 3 Изумруд». */
        fun line(itemName: (Int) -> String): String {
            fun name(id: Int) = if (id < 0) "?" else itemName(id)
            val price = buildString {
                append(priceA)
                append(' ')
                append(name(costA.itemId))
                costB?.let { second ->
                    append(" + ")
                    append(priceB ?: 0)
                    append(' ')
                    append(name(second.itemId))
                }
            }
            return "${resultName(itemName)} ← $price" + if (remaining == 0) " (нет)" else ""
        }
    }

    /** Открыт ли пакет для этого экрана контейнера. */
    fun belongsTo(openContainerId: Int): Boolean = containerId == openContainerId

    fun lines(itemName: (Int) -> String): List<String> = offers.map { it.line(itemName) }

    /** Уровень торговца словами: у жителя их пять, как в ванильном интерфейсе. */
    fun levelName(): String = levelName(villagerLevel)

    companion object {

        /** Номер пакета `Merchant Offers` в игре. */
        val PACKET_ID: Int get() = Protocol777.Play.MERCHANT_OFFERS

        /** Ванильный расчёт цены: спрос × множитель прибавляется к базовому количеству. */
        fun modifiedCost(cost: Cost, demand: Int, priceMultiplier: Float, specialPriceDiff: Int): Int {
            val bonus = kotlin.math.max(0f, kotlin.math.floor(cost.count * demand * priceMultiplier))
            return kotlin.math.max(1, cost.count + bonus.toInt() + specialPriceDiff)
        }

        fun levelName(level: Int): String = when (level) {
            1 -> "Новичок"
            2 -> "Подмастерье"
            3 -> "Ремесленник"
            4 -> "Эксперт"
            5 -> "Мастер"
            else -> "Уровень $level"
        }

        /** Опыт до следующего уровня: ванильные пороги 10, 70, 150 и 250. */
        fun progress(level: Int, xp: Int): Pair<Int, Int> {
            val from = when (level) { 1 -> 0; 2 -> 10; 3 -> 70; 4 -> 150; else -> 250 }
            val to = when (level) { 1 -> 10; 2 -> 70; 3 -> 150; else -> 250 }
            return (xp - from).coerceIn(0, to - from) to (to - from)
        }

        /**
         * @return null, если тело не разобралось (обрыв или незнакомый компонент предмета).
         */
        fun parse(body: ByteArray): Trades? = runCatching {
            val reader = ByteReader(body)
            val containerId = reader.readVarInt()
            val size = reader.readVarInt()
            if (size !in 0..MAX_OFFERS) return null
            val offers = ArrayList<Offer>(size)
            repeat(size) {
                val costA = readCost(reader) ?: return null
                // Результат сделки читается как обычный стек меню: те же компоненты.
                val result = readMenuStack(reader)
                val costB = if (reader.readBoolean()) readCost(reader) ?: return null else null
                val outOfStock = reader.readBoolean()
                val uses = reader.readInt()
                val maxUses = reader.readInt()
                val xp = reader.readInt()
                val specialPriceDiff = reader.readInt()
                val priceMultiplier = reader.readFloat()
                val demand = reader.readInt()
                offers += Offer(
                    costA = costA,
                    costB = costB,
                    result = result,
                    outOfStock = outOfStock,
                    uses = uses,
                    maxUses = maxUses,
                    xp = xp,
                    specialPriceDiff = specialPriceDiff,
                    priceMultiplier = priceMultiplier,
                    demand = demand,
                )
            }
            Trades(
                containerId = containerId,
                offers = offers,
                villagerLevel = reader.readVarInt(),
                villagerXp = reader.readVarInt(),
                showProgress = reader.readBoolean(),
                canRestock = reader.readBoolean(),
            )
        }.getOrNull()

        /** `ItemCost`: предмет, количество и предикат компонентов (у сделок он пустой). */
        private fun readCost(reader: ByteReader): Cost? {
            val itemId = reader.readVarInt()
            val count = reader.readVarInt()
            val components = reader.readVarInt()
            // Предикат компонентов читать нечем: у торговцев он пустой, иначе пакет брошен.
            if (components != 0) return null
            return Cost(itemId, count)
        }

        /** Столько сделок в одном пакете игра ещё не присылала. */
        private const val MAX_OFFERS = 256
    }
}

/**
 * Выбор сделки (`Select Trade`).
 *
 * Тело — один VarInt: номер сделки в списке с нуля. Сервер сам положит предметы в слоты
 * и покажет результат; без этого пакета нажатие на сделку ничего не делает.
 */
object SelectTrade {

    fun packet(offerIndex: Int): ByteArray = ByteWriter()
        .writeVarInt(offerIndex.coerceAtLeast(0))
        .toByteArray()
}
