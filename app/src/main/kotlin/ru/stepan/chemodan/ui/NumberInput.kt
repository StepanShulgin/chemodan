package ru.stepan.chemodan.ui

internal fun number(value: String): Double =
    value.filterNot { it.isWhitespace() || it == '\u00a0' }.replace(',', '.').toDoubleOrNull()
        ?: Double.NaN
