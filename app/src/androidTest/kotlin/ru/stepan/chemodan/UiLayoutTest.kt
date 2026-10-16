package ru.stepan.chemodan

import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.ui.*

@RunWith(AndroidJUnit4::class)
class UiLayoutTest {
    @Test
    fun semanticColorsRemainReadableInLightAndDarkThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val backgrounds = mutableSetOf<Int>()
            for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val configuration =
                    Configuration(instrumentation.targetContext.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                    }
                val context =
                    ContextThemeWrapper(
                        instrumentation.targetContext.createConfigurationContext(configuration),
                        R.style.Theme_Chemodan,
                    )
                val surface = context.themeColor(com.google.android.material.R.attr.colorSurface)
                backgrounds += surface
                val pairs =
                    listOf(
                        context.themeColor(com.google.android.material.R.attr.colorOnSurface) to
                            surface,
                        context.themeColor(
                            com.google.android.material.R.attr.colorOnSurfaceVariant
                        ) to surface,
                        context.themeColor(com.google.android.material.R.attr.colorOnPrimary) to
                            context.themeColor(com.google.android.material.R.attr.colorPrimary),
                        context.themeColor(R.attr.colorHeroForeground) to
                            context.themeColor(R.attr.colorHeroSurface),
                    )
                pairs.forEach { (text, background) ->
                    assertTrue(
                        "Small text needs sufficient contrast in mode $mode",
                        androidx.core.graphics.ColorUtils.calculateContrast(text, background) >= 4.5,
                    )
                }
                assertEquals(
                    mode == Configuration.UI_MODE_NIGHT_NO,
                    context.resources.getBoolean(R.bool.light_system_bars),
                )
                val body = LinearLayout(context)
                body.field(context.getString(R.string.trip_name))
                body.summaryRow("↗", "Расход", "Обмен", "125,555 KWD", "40 000 ₽")
            }
            assertEquals("Day and night use different surfaces", 2, backgrounds.size)
        }
    }

    @Test
    fun summaryColumnsDoNotOverlapOnNarrowScreenWithLargeText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val configuration =
                Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = 1.5f
                }
            val context =
                ContextThemeWrapper(
                    instrumentation.targetContext.createConfigurationContext(configuration),
                    R.style.Theme_Chemodan,
                )
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            root.summaryRow(
                "↗",
                "Длинное название расхода в путешествии",
                "Первый обмен · 30 USD\nВторой обмен · 50 USD",
                "123 456 789 USD",
                "9 876 543 ₽",
            )
            val width = context.dp(320)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            root.layout(0, 0, width, root.measuredHeight)
            val card = root.getChildAt(0) as MaterialCardView
            val inner = card.getChildAt(0) as LinearLayout
            val row = inner.getChildAt(0) as LinearLayout
            val texts = row.getChildAt(1)
            val amounts = row.getChildAt(2)
            assertTrue(texts.width > 0)
            assertTrue(amounts.width > 0)
            assertTrue(texts.right <= amounts.left)
            assertTrue(amounts.right <= row.width)
            assertEquals(texts.top, amounts.top)
        }
    }

    @Test
    fun inlineFieldsAlignWithCardHeadingsAndSelectorsAllowWrapping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Chemodan)
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val standalone = root.field("Название")
            val card = root.section("Маршрут")
            val country = card.selector("Страна") {}
            assertEquals(context.dimension(R.dimen.spacing_large), standalone.paddingLeft)
            assertEquals(0, country.paddingLeft)
            assertEquals(2, country.maxLines)
            assertFalse(country.includeFontPadding)
            assertEquals(context.dimension(R.dimen.control_height), country.minimumHeight)
            assertTrue(country.paddingTop >= context.dp(24))
        }
    }
}
