package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Время жизни частиц с сервера.
 *
 * Раньше все жили 0,18 с — дым взрыва и «пуф» смерти мигали и исчезали раньше, чем их можно
 * было увидеть. Тест держит порядок: дым долгий, искры короткие, неизвестное имя — среднее.
 */
class ServerParticlesTest {

    @Test
    fun `smoke and explosions live longer than sparks`() {
        assertTrue(ServerParticles.lifeSeconds("explosion") >= 0.8f)
        assertTrue(ServerParticles.lifeSeconds("large_smoke") >= 1.0f)
        assertTrue(ServerParticles.lifeSeconds("poof") >= 0.7f)
        assertTrue(
            ServerParticles.lifeSeconds("crit") < ServerParticles.lifeSeconds("explosion"),
            "искры короткие, дым долгий",
        )
        assertTrue(ServerParticles.lifeSeconds("crit") < ServerParticles.lifeSeconds("cloud"))
    }

    @Test
    fun `every lifetime is visible for at least a third of a second`() {
        // Меньше 0,33 с глазом не поймать: частица мигает. Раньше было 0,18 с.
        val names = listOf("explosion", "smoke", "poof", "flame", "splash", "crit", "dust", "snowflake", "unknown_thing")
        for (name in names) {
            assertTrue(ServerParticles.lifeSeconds(name) >= 0.33f, "$name живёт слишком мало")
        }
    }

    @Test
    fun `sprites are small enough not to cover the screen`() {
        // Все частицы были 1,2 блока: дым взрыва закрывал пол-экрана белыми квадратами.
        val names = listOf("explosion", "large_smoke", "smoke", "poof", "flame", "splash", "crit", "dust", "unknown_thing")
        for (name in names) {
            val size = ServerParticles.sizeFor(name)
            assertTrue(size <= 0.8f, "$name слишком крупная: $size")
            assertTrue(size >= 0.25f, "$name слишком мелкая: $size")
        }
        assertTrue(
            ServerParticles.sizeFor("explosion") > ServerParticles.sizeFor("crit"),
            "взрыв крупнее искры",
        )
    }
}
