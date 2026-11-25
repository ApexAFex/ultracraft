package mcplus.app.gl

/**
 * Седло на существе: есть ли оно и где его рисовать.
 *
 * В 26.3 седло — это слот экипировки (`SADDLE`, номер 7 в порядке `EquipmentSlot`), а не флаг в
 * метаданных. Мы и раньше не рисовали седло, хотя сервер присылает его в `Set Equipment`.
 */
object SaddleLook {

    /** Номер слота `SADDLE` в порядке `EquipmentSlot` (проверено по серверному jar). */
    const val SLOT = 7

    /** На кого седло вообще надевается. */
    fun fits(family: MobFamily): Boolean = when (family) {
        MobFamily.HORSE, MobFamily.QUADRUPED, MobFamily.CAMEL, MobFamily.STRIDER, MobFamily.RAVAGER -> true
        else -> false
    }

    /** Седло видно, только если оно надето и на этого моба оно подходит. */
    fun visible(family: MobFamily, saddleItem: Int): Boolean = saddleItem > 0 && fits(family)

    /**
     * Высота седла над точкой моба: у лошади и верблюда спина выше, у свиньи ниже.
     *
     * Величины подобраны по снимкам моделей: седло должно лежать на спине, а не висеть.
     */
    fun height(family: MobFamily): Float = when (family) {
        MobFamily.HORSE -> 1.15f
        MobFamily.CAMEL -> 1.30f
        MobFamily.STRIDER -> 1.20f
        MobFamily.RAVAGER -> 1.35f
        else -> 0.92f
    }

    /** Размер седла: у больших мобов оно крупнее. */
    fun size(family: MobFamily): Float = when (family) {
        MobFamily.CAMEL, MobFamily.RAVAGER -> 0.7f
        MobFamily.HORSE, MobFamily.STRIDER -> 0.6f
        else -> 0.45f
    }
}
