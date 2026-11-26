package mcplus.app.gl

import mcplus.net.MetaEntry

/**
 * Детёныш ли моб и насколько он раздут перед взрывом.
 *
 * Ванильные номера полей сняты с сервера (`tools/metadata-field.ps1`):
 * `AgeableMob.DATA_BABY_ID` = 16 (логическое, тип 8), `Creeper.DATA_SWELL_DIR` = 16 (целое,
 * тип 1) и `Creeper.DATA_IS_IGNITED` = 18. Один и тот же номер 16 значит у разных классов
 * разное, поэтому детёныша определяем ещё и по типу поля: у овцы это `BOOLEAN`, у крипера — `INT`.
 *
 * Размер детёныша в игре — `LivingEntity.getAgeScale()`: 0,5 у детёныша и 1,0 у взрослого.
 * Отдельных «детских» моделей у большинства зверей нет, поэтому масштаб — честное приближение
 * (у жителя и зомби детская модель своя, см. `docs/PARITY.md`).
 */
object EntityLook {

    /** Поле «детёныш» у [`AgeableMob`] и «направление раздувания» у крипера. */
    const val SHARED_FIELD = 16

    /** Тип поля «логическое значение» в `Set Entity Data`. */
    const val BOOLEAN_TYPE = 8

    /** Поле «подожжён» у крипера. */
    const val IGNITED_FIELD = 18

    /** Детёныш: поле 16 пришло логическим и равно 1. */
    fun isBaby(metadata: Map<Int, MetaEntry>): Boolean {
        val entry = metadata[SHARED_FIELD] ?: return false
        return entry.type == BOOLEAN_TYPE && entry.number != 0.0
    }

    /** Масштаб детёныша: половинный, как `getAgeScale()`. */
    const val BABY_SCALE = 0.5f

    /** Крипер подожжён (фитиль горит). */
    fun creeperIgnited(metadata: Map<Int, MetaEntry>): Boolean =
        (metadata[IGNITED_FIELD]?.number ?: 0.0) != 0.0

    /**
     * Направление раздувания крипера: 1 — раздувается, −1 — сдувается, 0 — покой.
     *
     * Приходит целым полем 16 — у крипера это не «детёныш».
     */
    fun creeperSwellDir(metadata: Map<Int, MetaEntry>): Int {
        val entry = metadata[SHARED_FIELD] ?: return 0
        if (entry.type == BOOLEAN_TYPE) return 0
        return entry.number.toInt()
    }

    /**
     * Раздувание крипера 0…1 по прошедшим секундам фитиля.
     *
     * Фитиль в игре — 30 тиков (1,5 с); в ванильном рендерере прогресс идёт от 0 до 1 и
     * дальше используется в его же формуле масштаба (`CreeperRenderer.scale`).
     */
    fun creeperSwell(seconds: Double): Float =
        (seconds / FUSE_SECONDS).coerceIn(0.0, 1.0).toFloat()

    /** Длительность фитиля крипера в секундах: 30 тиков по 50 мс. */
    const val FUSE_SECONDS = 1.5

    /**
     * Масштаб модели крипера по прогрессу раздувания — формула из `CreeperRenderer.scale`.
     *
     * Возвращает множители по X/Z и по Y: крипер не просто растёт, а «вздувается» —
     * по горизонтали сильнее, чем по высоте, с лёгкой пульсацией.
     */
    fun creeperScale(swell: Float): Pair<Float, Float> {
        val g = swell.coerceIn(0f, 1f)
        val h = 1f + kotlin.math.sin(g * 100f) * g * 0.01f
        val squared = g * g * g * g
        val horizontal = (1f + squared * 0.4f) * h
        val vertical = (1f + squared * 0.1f) / h
        return horizontal to vertical
    }
}
