package mcplus.app.gl

/**
 * Какие мобы рисуются моделью человека и какой у них скин.
 *
 * В Minecraft две раскладки скина: у игрока (`entity/player/wide/steve.png`) современная
 * 64×64, где у левых руки и ноги свои пиксели, а у мобов — классическая: левые конечности
 * берут текстуру правых, только зеркально. Это видно в самой игре: `HumanoidModel`
 * строит `left_arm` с `texOffs(40, 16)` и `mirror()`, а `PlayerModel` — с `texOffs(32, 48)`.
 * Поэтому у зомби файл скина 64×64, но правая половина пустая: игра её и не читает.
 *
 * Таблица чистая: ни GL, ни чтения файлов, поэтому её можно проверить тестами.
 */
object HumanoidLook {

    /** Скин моба по пути внутри `assets/minecraft/textures`. */
    fun lookOf(typeName: String): String? = when (typeName) {
        "zombie" -> "entity/zombie/zombie.png"
        "drowned" -> "entity/zombie/drowned.png"
        "husk" -> "entity/zombie/husk.png"
        "zombified_piglin" -> "entity/piglin/zombified_piglin.png"
        "piglin" -> "entity/piglin/piglin.png"
        "piglin_brute" -> "entity/piglin/piglin_brute.png"
        "pillager" -> "entity/illager/pillager.png"
        "vindicator" -> "entity/illager/vindicator.png"
        "evoker" -> "entity/illager/evoker.png"
        "illusioner" -> "entity/illager/illusioner.png"
        "skeleton" -> "entity/skeleton/skeleton.png"
        "stray" -> "entity/skeleton/stray.png"
        "bogged" -> "entity/skeleton/bogged.png"
        "parched" -> "entity/skeleton/parched.png"
        "wither_skeleton" -> "entity/skeleton/wither_skeleton.png"
        "enderman" -> "entity/enderman/enderman.png"
        // Гигант — тот же зомби, только в метаданных у него нет отдельной модели.
        "giant" -> "entity/zombie/zombie.png"
        else -> null
    }

    /**
     * Тонкие ли конечности.
     *
     * Ванильная `SkeletonModel` строит руки и ноги 2×12×2, а не 4×12×4: с толстыми
     * конечностями скелет превращается в белую плиту. У остальных гуманоидов — обычные.
     */
    fun slim(typeName: String): Boolean = when (typeName) {
        "skeleton", "stray", "bogged", "parched", "wither_skeleton" -> true
        else -> false
    }

    /** Гуманоид ли это: нужен для руки с предметом и брони. */
    fun isHumanoid(typeName: String): Boolean = lookOf(typeName) != null

    /**
     * Скин «манекена» — сущности-болванки на модели игрока.
     *
     * `Mannequin` в 26.3 наследует `Avatar` (как игрок), поэтому рисуется моделью игрока, а не
     * мобовой: у мобовой раскладки левые конечности зеркалятся, и скин игрока на ней разъезжается.
     * Профиль из метаданных пока не читаем — показываем стандартный скин.
     */
    fun playerSkin(typeName: String): String? = when (typeName) {
        "mannequin" -> "entity/player/wide/steve.png"
        else -> null
    }

    /**
     * Держит ли моб руки вытянутыми вперёд.
     *
     * В игре это `ZombieModel.setupAnim`: у зомби, кадавра, утопленника и зомби-пиглина
     * обе руки повёрнуты на 90° вперёд, поэтому они и узнаются издалека.
     */
    fun armsForward(typeName: String): Boolean = when (typeName) {
        "zombie", "husk", "drowned", "zombified_piglin" -> true
        else -> false
    }
}
