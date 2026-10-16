package ru.stepan.chemodan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BudgetCalculatorTest {
    private val calculator = BudgetCalculator()

    @Test
    fun `calculates remaining amount and progress`() {
        val result = calculator.calculate(50_000.0, 12_500.0)
        assertEquals(37_500.0, result.remaining, 0.0)
        assertEquals(0.25f, result.progress, 0.0001f)
    }

    @Test
    fun `clamps progress when expenses exceed budget without hiding negative remainder`() {
        val result = calculator.calculate(50_000.0, 60_000.0)
        assertEquals(-10_000.0, result.remaining, 0.0)
        assertEquals(1.0f, result.progress, 0.0f)
    }

    @Test
    fun `requires positive budget`() {
        assertThrows(IllegalArgumentException::class.java) { calculator.calculate(0.0, 1.0) }
    }
}
