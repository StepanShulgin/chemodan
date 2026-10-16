package ru.stepan.chemodan.domain

import org.junit.Assert.*
import org.junit.Test

class DecimalAmountsTest {
    @Test
    fun `adding and spending decimal amounts leaves no binary remainder`() {
        assertEquals(.3, listOf(.1, .2).decimalSum(), 0.0)
        assertEquals(.2, .3.subtractAmount(.1), 0.0)
        assertEquals(0.0, .3.subtractAmount(.1).subtractAmount(.2), 0.0)
    }

    @Test
    fun `rejects invisible fractional units and unbounded input`() {
        requireMoney(125.555, "KWD")
        requireMoney(125.55, "USD")
        requireMoney(125.0, "JPY")
        listOf(.001, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE, -1.0).forEach {
            assertThrows(InputFailure::class.java) { requireMoney(it, "RUB") }
        }
        assertThrows(InputFailure::class.java) { requireMoney(125.55, "JPY") }
    }

    @Test
    fun `allocating three decimal currency conserves every ruble kopeck`() {
        val calculator = ExchangeCalculator()
        val total = 125.555
        var amount = total
        var rubles = 40000.0
        val costs =
            listOf(.555, 100.0, 25.0).map { part ->
                calculator.allocateRubleCost(part, amount, rubles).also { cost ->
                    amount = amount.subtractAmount(part)
                    rubles = rubles.subtractAmount(cost)
                }
            }
        assertEquals(40000.0, costs.decimalSum(), 0.0)
        assertEquals(0.0, amount, 0.0)
        assertEquals(0.0, rubles, 0.0)
    }
}
