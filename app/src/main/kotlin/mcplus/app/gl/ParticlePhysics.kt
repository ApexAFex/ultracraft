package mcplus.app.gl

/**
 * Физика частиц: гравитация, сопротивление и отскок от твёрдого блока.
 *
 * В ванили частицы падают, слегка тормозят и подпрыгивают, ударившись о землю; у нас они
 * раньше проваливались сквозь блоки, поэтому искры и крошки «уходили под пол».
 *
 * Расчёт вынесен отдельно, чтобы проверяться тестом без окна и мира.
 */
object ParticlePhysics {

    /** Ускорение свободного падения для частиц (блоков в секунду в квадрате). */
    const val GRAVITY = 3.0

    /** Множитель скорости за кадр: в ванили частицы заметно тормозят. */
    const val DRAG = 0.985

    /** Доля вертикальной скорости, которая остаётся после удара о пол. */
    const val BOUNCE = 0.35

    /** Ниже этой скорости отскок не заметен: частица просто лежит. */
    const val REST_SPEED = 0.2

    data class State(
        val x: Double,
        val y: Double,
        val z: Double,
        val vx: Double,
        val vy: Double,
        val vz: Double,
    )

    /**
     * Один шаг физики.
     *
     * [solid] отвечает, твёрдый ли блок в целых координатах: если под частицей твёрдо, она
     * останавливается на верхней грани и слегка подпрыгивает.
     */
    fun step(state: State, deltaSeconds: Double, solid: (Int, Int, Int) -> Boolean): State {
        if (deltaSeconds <= 0.0) return state
        var vx = state.vx * Math.pow(DRAG, deltaSeconds * 60.0)
        var vz = state.vz * Math.pow(DRAG, deltaSeconds * 60.0)
        var vy = state.vy - GRAVITY * deltaSeconds
        var x = state.x + vx * deltaSeconds
        var y = state.y + vy * deltaSeconds
        var z = state.z + vz * deltaSeconds

        // Проверяем клетку под частицей: так искры не проваливаются сквозь пол.
        val blockY = Math.floor(y).toInt()
        val belowSolid = solid(Math.floor(x).toInt(), blockY, Math.floor(z).toInt())
        if (belowSolid) {
            y = blockY + 1.0
            vy = if (-vy > REST_SPEED) -vy * BOUNCE else 0.0
            vx *= 0.6
            vz *= 0.6
        }
        return State(x, y, z, vx, vy, vz)
    }
}
