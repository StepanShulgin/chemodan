package ru.stepan.chemodan.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormattingTest {
    @Test
    fun `respects currency fraction digits`() {
        assertEquals("125,555 KWD", money(125.555, "KWD"))
        assertEquals("125,555 BHD", money(125.555, "BHD"))
        assertEquals("126 JPY", money(125.555, "JPY"))
        assertEquals("125,56 USD", money(125.555, "USD"))
    }

    @Test
    fun `presentation uses same rounding rule as financial calculations`() {
        assertEquals("0,01 ₽", money(.005, "RUB"))
    }
}
