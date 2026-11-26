package mcplus.app.gl

import mcplus.net.MetaEntry

/**
 * Шерсть овцы: краситель и «острижена».
 *
 * В ванили и то и другое лежит в одном байте `Sheep.DATA_WOOL_ID`: младшие четыре бита —
 * номер красителя, бит `0x10` — «острижена». Номер поля (18) снят с самого сервера
 * (`tools/metadata-field.ps1 net.minecraft.world.entity.animal.sheep.Sheep`), а не угадан:
 * в javap поля идут в другом порядке, чем в сети.
 *
 * Остриженная овца не рисует шерсть вовсе, а цветная — красит текстуру шерсти
 * (`sheep_wool.png` серая) цветом красителя.
 */
object SheepLook {

    /** Номер поля шерсти в `Set Entity Data`. */
    const val WOOL_FIELD = 18

    /** Бит «острижена» в том же байте (`Sheep.isSheared`). */
    const val SHEARED_BIT = 0x10

    /** Байт шерсти; без поля считаем белую неостриженную овцу. */
    fun woolByte(metadata: Map<Int, MetaEntry>): Int =
        metadata[WOOL_FIELD]?.number?.toInt() ?: 0

    /** Овца острижена: бит `0x10`. */
    fun isSheared(metadata: Map<Int, MetaEntry>): Boolean =
        (woolByte(metadata) and SHEARED_BIT) != 0

    /** Номер красителя (0…15): младшие четыре бита байта. */
    fun colorId(metadata: Map<Int, MetaEntry>): Int = woolByte(metadata) and 0x0F

    /** Цвет текстуры шерсти: белый для неизвестного номера. */
    fun woolTint(metadata: Map<Int, MetaEntry>): Int = DyeColors.rgb(colorId(metadata))
}
