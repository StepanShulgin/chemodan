package ru.stepan.chemodan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.stepan.chemodan.data.ExchangeSourceDraft
import ru.stepan.chemodan.data.ExchangeStep

class ExchangeCalculatorTest {
    private val calculator = ExchangeCalculator()

    @Test
    fun `preserves three fraction digits of foreign currency`() {
        val source =
            calculator.validate(
                ExchangeSourceDraft(
                    "Кувейт",
                    listOf(ExchangeStep("RUB", 40000.0, 500.0, "KWD", 125.555)),
                )
            )
        assertEquals(125.555, source.amount, 0.0)
        val first = calculator.allocateRubleCost(.555, source.amount, source.rubleCost)
        assertEquals(
            source.rubleCost,
            first + calculator.allocateRubleCost(125.0, 125.0, source.rubleCost - first),
            .000001,
        )
    }

    @Test
    fun `does not silently accept a small overspend or a gap between steps`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.allocateRubleCost(1.0000001, 1.0, 100.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculator.validate(
                ExchangeSourceDraft(
                    "Ошибка",
                    listOf(
                        ExchangeStep("RUB", 100.0, 0.0, "KWD", 1.001),
                        ExchangeStep("KWD", 1.002, 0.0, "THB", 100.0),
                    ),
                )
            )
        }
    }

    @Test
    fun `calculates ruble cost from real multi-step exchange including fees`() {
        val result =
            calculator.validate(
                ExchangeSourceDraft(
                    "Наличные через доллары",
                    listOf(
                        ExchangeStep("RUB", 9_200.0, 200.0, "USD", 100.0),
                        ExchangeStep("USD", 100.0, 2.0, "THB", 3_400.0),
                    ),
                )
            )

        assertEquals("THB", result.currency)
        assertEquals(3_400.0, result.amount, 0.0)
        assertEquals(9_200.0, result.rubleCost, 0.0)
    }

    @Test
    fun `allocates partial expense proportionally and takes exact remainder on final spend`() {
        assertEquals(2_300.0, calculator.allocateRubleCost(850.0, 3_400.0, 9_200.0), 0.0)
        assertEquals(6_900.0, calculator.allocateRubleCost(2_550.0, 2_550.0, 6_900.0), 0.0)
    }

    @Test
    fun `rejects commission greater than amount surrendered`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.validate(
                ExchangeSourceDraft(
                    "Ошибка",
                    listOf(ExchangeStep("RUB", 100.0, 100.0, "THB", 20.0)),
                )
            )
        }
    }

    @Test
    fun `permits independently chosen final currency`() {
        val source =
            calculator.validate(
                ExchangeSourceDraft("Доллары", listOf(ExchangeStep("RUB", 100.0, 0.0, "USD", 1.0)))
            )
        assertEquals("USD", source.currency)
    }

    @Test
    fun `estimates ruble price from requested reference rate`() {
        assertEquals(2_125.0, calculator.estimateRubleCost(850.0, 0.4), 0.0)
    }

    @Test
    fun `rejects overspending and invalid amounts`() {
        listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 101.0).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) {
                calculator.allocateRubleCost(amount, 100.0, 9000.0)
            }
        }
    }

    @Test
    fun `rejects discontinuous currency chain`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.validate(
                ExchangeSourceDraft(
                    "Ошибка",
                    listOf(
                        ExchangeStep("RUB", 9000.0, 0.0, "USD", 100.0),
                        ExchangeStep("EUR", 100.0, 0.0, "THB", 3400.0),
                    ),
                )
            )
        }
    }

    @Test
    fun `rejects partial forwarding of exchange step`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.validate(
                ExchangeSourceDraft(
                    "Ошибка",
                    listOf(
                        ExchangeStep("RUB", 9000.0, 0.0, "USD", 100.0),
                        ExchangeStep("USD", 90.0, 0.0, "THB", 3400.0),
                    ),
                )
            )
        }
    }
}
