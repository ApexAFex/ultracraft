package mcplus.app.gl

/**
 * Цвета красителей: имя, номер и цвет текстуры.
 *
 * Ваниль красит текстуру там, где предмет или часть существа получают цвет: шерсть овцы
 * (`DyeColor.getTextureDiffuseColor`), кожаная броня, флаги. Числа сняты с самого клиента
 * 26.3 (`tools/dye-colors.ps1`), а не из вики: у белого цвета это не чистый белый, а
 * `0xF9FFFE`, и подбор «на глаз» дал бы заметно другой оттенок.
 */
object DyeColors {

    /** Один краситель: номер (он же порядок в таблице) и цвет текстуры. */
    data class Dye(val name: String, val id: Int, val rgb: Int)

    /** Все 16 красителей в порядке номеров: белый, оранжевый, …, чёрный. */
    val ALL: List<Dye> = listOf(
        Dye("white", 0, 0xF9FFFE),
        Dye("orange", 1, 0xF9801D),
        Dye("magenta", 2, 0xC74EBD),
        Dye("light_blue", 3, 0x3AB3DA),
        Dye("yellow", 4, 0xFED83D),
        Dye("lime", 5, 0x80C71F),
        Dye("pink", 6, 0xF38BAA),
        Dye("gray", 7, 0x474F52),
        Dye("light_gray", 8, 0x9D9D97),
        Dye("cyan", 9, 0x169C9C),
        Dye("purple", 10, 0x8932B8),
        Dye("blue", 11, 0x3C44AA),
        Dye("brown", 12, 0x835432),
        Dye("green", 13, 0x5E7C16),
        Dye("red", 14, 0xB02E26),
        Dye("black", 15, 0x1D1D21),
    )

    /** Цвет текстуры по номеру красителя; неизвестный номер — белый (как у ванильного нуля). */
    fun rgb(id: Int): Int = ALL.getOrNull(id)?.rgb ?: ALL[0].rgb

    /** Имя по номеру: `white`, `red` и так далее (так оно приходит в NBT овцы). */
    fun name(id: Int): String = ALL.getOrNull(id)?.name ?: ALL[0].name
}
