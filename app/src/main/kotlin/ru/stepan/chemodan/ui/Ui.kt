package ru.stepan.chemodan.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import ru.stepan.chemodan.R

internal data class ScreenPage(
    val root: LinearLayout,
    val body: LinearLayout,
    val heading: TextView,
    val description: TextView?,
    val list: RecyclerView?,
)

internal fun Context.page(
    title: String,
    subtitle: String? = null,
    listContent: Boolean = false,
): ScreenPage {
    val root =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(android.R.attr.colorBackground))
        }
    val body =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dimension(R.dimen.screen_gutter),
                dimension(R.dimen.spacing_small),
                dimension(R.dimen.screen_gutter),
                dimension(R.dimen.screen_gutter),
            )
        }
    val heading =
        body.label(title, 28f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -.025f
        }
    val description =
        subtitle?.let {
            body.label(it, 15f).apply {
                setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            }
        }
    body.space(12)
    val list =
        if (listContent)
            RecyclerView(this).apply {
                id = R.id.screen_list
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                setPadding(0, 0, 0, dimension(R.dimen.screen_gutter))
            }
        else null
    root.addView(
        list
            ?: NestedScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(body)
            },
        LinearLayout.LayoutParams(-1, 0, 1f),
    )
    return ScreenPage(root, body, heading, description, list)
}

internal fun LinearLayout.space(height: Int) =
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(height)))

internal fun LinearLayout.label(text: String, size: Float = 16f): TextView =
    TextView(context).apply {
        this.text = text
        textSize = size
        includeFontPadding = false
        gravity = Gravity.START
        textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        setTextColor(context.themeColor(MaterialR.attr.colorOnSurface))
        setLineSpacing(context.dp(4).toFloat(), 1f)
        setPadding(0, context.dp(4), 0, context.dp(4))
        addView(this, LinearLayout.LayoutParams(-1, -2))
    }

internal fun LinearLayout.action(
    text: String,
    onClick: () -> Unit,
    id: Int? = null,
    outlined: Boolean = false,
): MaterialButton =
    MaterialButton(context).apply {
        this.text = text
        id?.let { this.id = it }
        setOnClickListener { onClick() }
        isAllCaps = false
        textSize = 15f
        cornerRadius = context.dp(18)
        insetTop = 0
        insetBottom = 0
        includeFontPadding = false
        minHeight = context.dimension(R.dimen.action_height)
        minimumWidth = 0
        if (outlined) {
            strokeWidth = 0
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
            minHeight = context.dimension(R.dimen.touch_target)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        layoutParams =
            LinearLayout.LayoutParams(if (outlined) -2 else -1, -2).apply {
                topMargin = context.dimension(R.dimen.spacing_medium)
            }
        addView(this)
    }

internal fun LinearLayout.fixedAction(text: String, id: Int, onClick: () -> Unit): MaterialButton {
    val footer =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.dimension(R.dimen.screen_gutter),
                context.dimension(R.dimen.spacing_medium),
                context.dimension(R.dimen.screen_gutter),
                context.dimension(R.dimen.spacing_large),
            )
            setBackgroundColor(context.themeColor(android.R.attr.colorBackground))
        }
    addView(footer)
    return footer.action(text, onClick, id).apply {
        (layoutParams as LinearLayout.LayoutParams).topMargin = 0
    }
}

internal fun LinearLayout.field(
    label: String,
    hint: String = label,
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    accessibilityLabel: String = label,
): TextInputEditText {
    val insideCard =
        generateSequence(parent as? View) { it.parent as? View }.any { it is MaterialCardView }
    val layout =
        TextInputLayout(context).apply {
            this.hint = label
            placeholderText = hint.takeIf { it != label }
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_FILLED
            boxBackgroundColor =
                if (insideCard) android.graphics.Color.TRANSPARENT
                else context.themeColor(MaterialR.attr.colorSurface)
            boxStrokeColor = context.themeColor(MaterialR.attr.colorPrimary)
            boxStrokeWidth = 0
            boxStrokeWidthFocused = 0
            defaultHintTextColor =
                ColorStateList.valueOf(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
            boxCollapsedPaddingTop = context.dimension(R.dimen.spacing_small)
            setBoxCornerRadii(
                16f * resources.displayMetrics.density,
                16f * resources.displayMetrics.density,
                16f * resources.displayMetrics.density,
                16f * resources.displayMetrics.density,
            )
        }
    val edit =
        TextInputEditText(context).apply {
            background = null
            this.inputType = inputType
            contentDescription = accessibilityLabel
            id = stableId(accessibilityLabel)
            val horizontal = if (insideCard) 0 else context.dimension(R.dimen.spacing_large)
            setPadding(
                horizontal,
                context.dp(32),
                horizontal,
                context.dimension(R.dimen.spacing_small),
            )
            minimumHeight = context.dimension(R.dimen.control_height)
            includeFontPadding = false
            gravity = Gravity.START or Gravity.TOP
            textAlignment = View.TEXT_ALIGNMENT_GRAVITY
            setTextColor(context.themeColor(MaterialR.attr.colorOnSurface))
            textSize = 16f
            isSingleLine = true
        }
    layout.addView(edit)
    addView(
        layout,
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dimension(R.dimen.spacing_medium)
        },
    )
    return edit
}

internal fun LinearLayout.columns(): Pair<LinearLayout, LinearLayout> {
    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    addView(row, LinearLayout.LayoutParams(-1, -2))
    val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    row.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(6) })
    row.addView(right, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(6) })
    return left to right
}

internal fun LinearLayout.section(title: String): LinearLayout =
    panel().apply {
        label(title, 14f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    }

internal data class SummaryRowViews(
    val card: View,
    val symbol: TextView,
    val title: TextView,
    val subtitle: TextView,
    val multilineSubtitle: TextView,
    val value: TextView,
    val secondary: TextView,
)

internal fun LinearLayout.summaryRow(
    symbol: String,
    title: String,
    subtitle: String,
    value: String,
    secondary: String,
    onClick: (() -> Unit)? = null,
): SummaryRowViews {
    val inner = panel(onClick)
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
    inner.addView(row)
    val icon =
        TextView(context).apply {
            text = symbol
            textSize = 23f
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = context.rounded(MaterialR.attr.colorPrimaryContainer, 16)
            setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    row.addView(
        icon,
        LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply {
            marginEnd = context.dp(12)
        },
    )
    val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    row.addView(texts, LinearLayout.LayoutParams(0, -2, 1.5f))
    val titleView =
        texts.label(title, 16f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    val multiline = subtitle.contains('\n')
    val subtitleView =
        texts.label(subtitle, 12f).apply {
            visibility = if (multiline) View.GONE else View.VISIBLE
            setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
    val amounts =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(8), 0, 0, 0)
        }
    row.addView(amounts, LinearLayout.LayoutParams(0, -2, 1f))
    val valueView =
        amounts.label(value, 17f).apply {
            gravity = Gravity.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                12,
                17,
                1,
                android.util.TypedValue.COMPLEX_UNIT_SP,
            )
            maxLines = 2
        }
    val secondaryView =
        amounts.label(secondary, 12f).apply {
            gravity = Gravity.END
            setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
            maxLines = 2
        }
    val multilineView =
        inner.label(subtitle, 12f).apply {
            visibility = if (multiline) View.VISIBLE else View.GONE
            setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
            (layoutParams as LinearLayout.LayoutParams).marginStart = context.dp(60)
        }
    return SummaryRowViews(
        inner.parent as View,
        icon,
        titleView,
        subtitleView,
        multilineView,
        valueView,
        secondaryView,
    )
}

internal fun Context.rounded(color: Int, radius: Int) =
    GradientDrawable().apply {
        setColor(themeColor(color))
        cornerRadius = dp(radius).toFloat()
    }

internal fun LinearLayout.selector(
    label: String,
    displayLabel: String = label,
    onClick: () -> Unit,
): TextInputEditText =
    field(displayLabel, accessibilityLabel = label).apply {
        inputType = android.text.InputType.TYPE_NULL
        isSingleLine = false
        maxLines = 2
        setHorizontallyScrolling(false)
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        isClickable = true
        setOnClickListener { onClick() }
        requireNotNull(inputLayout).apply {
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.ic_chevron)
            setEndIconTintList(
                ColorStateList.valueOf(context.themeColor(MaterialR.attr.colorPrimary))
            )
            setEndIconContentDescription(uiText(R.string.choose_field, label))
            setEndIconOnClickListener { onClick() }
            findViewById<View>(com.google.android.material.R.id.text_input_end_icon)?.translationY =
                context.dp(8).toFloat()
        }
    }

internal fun LinearLayout.panel(
    onClick: (() -> Unit)? = null,
    dark: Boolean = false,
): LinearLayout {
    val card =
        MaterialCardView(context).apply {
            radius = context.dimension(R.dimen.card_radius).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(
                context.themeColor(
                    if (dark) R.attr.colorHeroSurface else MaterialR.attr.colorSurface
                )
            )
            onClick?.let {
                isClickable = true
                isFocusable = true
                setOnClickListener { it() }
            }
        }
    val inner =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dimension(R.dimen.card_padding))
        }
    card.addView(inner)
    addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
    return inner
}

internal fun LinearLayout.card(text: String, onClick: (() -> Unit)? = null): TextView =
    panel(onClick).label(text, 15f).apply {
        setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
    }

internal fun LinearLayout.eyebrow(text: String) =
    label(text, 11f).apply {
        letterSpacing = .09f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
    }

internal fun LinearLayout.emptyState(symbol: String, title: String, explanation: String) {
    panel().apply {
        label(symbol, 36f)
        space(12)
        label(title, 21f).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        label(explanation, 15f)
            .setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
    }
}

internal val EditText.inputLayout: TextInputLayout?
    get() = generateSequence(parent) { it.parent }.filterIsInstance<TextInputLayout>().firstOrNull()

internal fun EditText.error(message: String?) {
    inputLayout?.error = message
}

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun Context.dimension(id: Int) = resources.getDimensionPixelSize(id)

internal fun Context.themeColor(@androidx.annotation.AttrRes attribute: Int): Int =
    com.google.android.material.color.MaterialColors.getColor(this, attribute, "Theme.Chemodan")

internal fun stableId(key: String) = (key.hashCode() and 0x00ffffff) or 0x01000000

internal fun money(value: Double, currency: String): String =
    java.text.NumberFormat.getNumberInstance(Locale("ru", "RU"))
        .apply {
            maximumFractionDigits = ru.stepan.chemodan.domain.currencyDigits(currency)
            roundingMode = java.math.RoundingMode.HALF_UP
        }
        .format(value) + " " + if (currency == "RUB") "₽" else currency

internal fun rateValue(value: Double, currency: String): String =
    java.text.NumberFormat.getNumberInstance(Locale("ru", "RU"))
        .apply { maximumFractionDigits = 6 }
        .format(value) + " " + currency

internal fun Context.dateRange(start: Long, end: Long): String {
    if (start == 0L) return uiText(R.string.dates_not_selected)
    val format =
        SimpleDateFormat("d MMM yyyy", Locale("ru", "RU")).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    return "${format.format(Date(start))} — ${format.format(Date(end))}"
}

internal fun androidx.fragment.app.Fragment.dateRange(start: Long, end: Long): String =
    requireContext().dateRange(start, end)

internal fun Context.uiText(@androidx.annotation.StringRes id: Int, vararg args: Any?): String =
    getString(id, *args)

internal fun View.uiText(@androidx.annotation.StringRes id: Int, vararg args: Any?): String =
    context.getString(id, *args)

internal fun androidx.fragment.app.Fragment.uiText(
    @androidx.annotation.StringRes id: Int,
    vararg args: Any?,
): String = getString(id, *args)
