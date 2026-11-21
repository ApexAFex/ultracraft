package mcplus.app.gl

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Полоски в меню: варка и цена зачарования.
 *
 * Числа приходят отдельным пакетом (`Container Set Data`), а не слотами: без них меню
 * зельеварения выглядит пустым, а кнопки зачарования — без цен.
 */
class ContainerProgressTest {

    @Test
    fun `brew progress goes from zero to full`() {
        assertEquals(0f, ContainerProgress.brewFraction(ContainerProgress.BREW_TICKS))
        assertEquals(0.5f, ContainerProgress.brewFraction(ContainerProgress.BREW_TICKS / 2))
        assertEquals(1f, ContainerProgress.brewFraction(0), "варка закончена — полоска полная")
        // Отрицательное время и переполнение не ломают полоску.
        assertEquals(1f, ContainerProgress.brewFraction(-40))
        assertEquals(0f, ContainerProgress.brewFraction(ContainerProgress.BREW_TICKS * 2))
    }

    @Test
    fun `fuel goes from zero to one`() {
        assertEquals(0f, ContainerProgress.fuelFraction(0))
        assertEquals(0.5f, ContainerProgress.fuelFraction(ContainerProgress.FUEL_TICKS / 2))
        assertEquals(1f, ContainerProgress.fuelFraction(ContainerProgress.FUEL_TICKS))
        assertEquals(1f, ContainerProgress.fuelFraction(100), "больше полного бака не бывает")
    }

    @Test
    fun `enchant costs are three numbers with zero for an empty button`() {
        assertEquals(listOf(0, 0, 0), ContainerProgress.enchantCosts(null, null, null))
        assertEquals(listOf(1, 2, 3), ContainerProgress.enchantCosts(1, 2, 3))
        assertEquals(listOf(0, 5, 0), ContainerProgress.enchantCosts(0, 5, null))
        // Отрицательная цена — тоже «пусто», а не минус на экране.
        assertEquals(listOf(0, 0, 0), ContainerProgress.enchantCosts(-3, -1, null))
    }
}
