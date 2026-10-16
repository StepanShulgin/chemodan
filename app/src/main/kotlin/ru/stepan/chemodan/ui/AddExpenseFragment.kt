package ru.stepan.chemodan.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.stepan.chemodan.MainActivity
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.ExchangeSourceEntity
import ru.stepan.chemodan.data.ExpenseAllocation
import ru.stepan.chemodan.data.ReferenceRateResult
import ru.stepan.chemodan.domain.subtractAmount

@AndroidEntryPoint
class AddExpenseFragment : StatefulFormFragment() {
    @Inject lateinit var calculator: ru.stepan.chemodan.domain.ExchangeCalculator
    private val viewModel: TripDetailsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val tripId = requireArguments().getLong("trip_id")
        val (root, body) = requireContext().page(uiText(R.string.new_expense))
        val expenseSection = body.section(uiText(R.string.expense))
        val title =
            expenseSection
                .field(uiText(R.string.expense_title), uiText(R.string.expense_title_example))
                .apply { setText(draft.getString("title", "")) }
        var currency = draft.getString("currency", "").orEmpty()
        val (amountColumn, currencyColumn) = expenseSection.columns()
        val currencyField =
            currencyColumn.selector(uiText(R.string.expense_currency), uiText(R.string.currency)) {
                SelectionSheet.open(this, "expense_currency")
            }
        val amount =
            amountColumn
                .field(uiText(R.string.amount), uiText(R.string.expense_amount_example), DECIMAL)
                .apply {
                    setText(draft.getString("amount", ""))
                    textSize = 25f
                }
        body.space(16)
        body.label(uiText(R.string.calculation_method), 14f)
        val modes =
            MaterialButtonToggleGroup(requireContext()).apply {
                isSingleSelection = true
                isSelectionRequired = true
            }
        listOf(
                ID_MODE_AUTOMATIC to uiText(R.string.automatic_rate),
                ID_MODE_MANUAL to uiText(R.string.manual_exchange),
            )
            .forEach { (id, text) ->
                modes.addView(
                    MaterialButton(requireContext()).apply {
                        this.id = id
                        this.text = text
                        isCheckable = true
                        isAllCaps = false
                        cornerRadius = context.dp(16)
                        includeFontPadding = false
                        textSize = 14f
                        insetTop = 0
                        insetBottom = 0
                        setPadding(context.dp(12), 0, context.dp(12), 0)
                        val checked =
                            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                        backgroundTintList =
                            ColorStateList(
                                checked,
                                intArrayOf(
                                    context.themeColor(MaterialR.attr.colorPrimary),
                                    context.themeColor(MaterialR.attr.colorSurface),
                                ),
                            )
                        setTextColor(
                            ColorStateList(
                                checked,
                                intArrayOf(
                                    context.themeColor(MaterialR.attr.colorOnPrimary),
                                    context.themeColor(MaterialR.attr.colorPrimary),
                                ),
                            )
                        )
                        strokeColor =
                            ColorStateList.valueOf(context.themeColor(MaterialR.attr.colorPrimary))
                        strokeWidth = context.dp(1)
                        layoutParams =
                            LinearLayout.LayoutParams(
                                0,
                                context.dimension(R.dimen.action_height),
                                1f,
                            )
                    }
                )
            }
        body.addView(modes)
        var automatic = draft.getBoolean("automatic", true)
        modes.check(if (automatic) ID_MODE_AUTOMATIC else ID_MODE_MANUAL)
        val manual = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(manual)
        var sourceId = draft.getLong("source")
        var secondSourceId = draft.getLong("second_source")
        var split = draft.getBoolean("split")
        var sources = emptyList<ExchangeSourceEntity>()
        val source =
            manual.selector(uiText(R.string.exchange_source)) {
                SelectionSheet.openSources(this, "expense_source", tripId)
            }
        parentFragmentManager.setFragmentResultListener("expense_source", viewLifecycleOwner) {
            _,
            result ->
            sourceId = result.getString("code")?.toLongOrNull() ?: 0
            sources
                .firstOrNull { it.id == sourceId }
                ?.let { selected ->
                    if (currency != selected.currency || secondSourceId == sourceId)
                        secondSourceId = 0
                    currency = selected.currency
                }
            renderExpense?.invoke()
        }
        source.id = ID_SOURCE_SPINNER
        source.ellipsize = TextUtils.TruncateAt.END
        val splitFields =
            LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val splitToggle = manual.action("", {}, stableId("split_expense"), outlined = true)
        manual.addView(splitFields)
        val firstAmount =
            splitFields
                .field(
                    uiText(R.string.first_allocation_amount),
                    uiText(R.string.allocation_amount_hint),
                    DECIMAL,
                )
                .apply { setText(draft.getString("first_amount", "")) }
        val secondSource =
            splitFields.selector(uiText(R.string.second_exchange_source)) {
                SelectionSheet.openSources(
                    this,
                    "expense_source_second",
                    tripId,
                    currency,
                    sourceId,
                )
            }
        val secondAmount = splitFields.label("", 14f)
        parentFragmentManager.setFragmentResultListener(
            "expense_source_second",
            viewLifecycleOwner,
        ) { _, result ->
            secondSourceId = result.getString("code")?.toLongOrNull() ?: 0
            renderExpense?.invoke()
        }
        splitToggle.setOnClickListener {
            split = !split
            renderExpense?.invoke()
        }
        manual.action(
            uiText(R.string.create_exchange_chain),
            {
                (requireActivity() as MainActivity).show(
                    ExchangeSourceFragment.newInstance(tripId, currency.ifEmpty { "USD" }),
                    true,
                )
            },
            outlined = true,
        )
        val info = body.panel()
        val caption = info.eyebrow("")
        val preview = info.label("", 24f)
        val explanation =
            info.label("", 13f).apply {
                setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
            }
        var rateState: RateUiState = viewModel.automaticRate.value ?: RateUiState.Idle
        fun currentRate(): ReferenceRateResult? =
            (rateState as? RateUiState.Available)?.result?.takeIf { it.rate.quote == currency }
        val retry =
            info.action(
                uiText(R.string.retry_rate),
                { viewModel.loadAutomaticRate(currency, force = true) },
                outlined = true,
            )
        val save =
            root.fixedAction(uiText(R.string.save_expense), ID_SAVE_EXPENSE) {
                title.error(if (title.text.isNullOrBlank()) uiText(R.string.enter_title) else null)
                val value = number(amount.text.toString())
                amount.error(
                    if (!value.isFinite() || value <= 0) uiText(R.string.enter_positive_amount)
                    else null
                )
                if (!title.text.isNullOrBlank() && value.isFinite() && value > 0) {
                    if (automatic) {
                        val snapshot = currentRate()?.rate
                        if (snapshot == null || snapshot.quote != currency)
                            Snackbar.make(
                                    root,
                                    uiText(R.string.wait_for_rate),
                                    Snackbar.LENGTH_LONG,
                                )
                                .show()
                        else {
                            saveExpenseButton?.isEnabled = false
                            viewModel.addAutomaticExpense(
                                tripId,
                                title.text.toString(),
                                amount.text.toString(),
                                snapshot,
                            )
                        }
                    } else if (sources.any { it.id == sourceId && it.currency == currency }) {
                        val part = number(firstAmount.text.toString())
                        if (split && (!part.isFinite() || part <= 0 || part >= value)) {
                            firstAmount.error(uiText(R.string.invalid_first_allocation))
                        } else if (
                            split &&
                                (secondSourceId == sourceId ||
                                    sources.none {
                                        it.id == secondSourceId && it.currency == currency
                                    })
                        ) {
                            Snackbar.make(
                                    root,
                                    uiText(R.string.choose_second_source, currency),
                                    Snackbar.LENGTH_LONG,
                                )
                                .show()
                        } else {
                            firstAmount.error(null)
                            saveExpenseButton?.isEnabled = false
                            if (split)
                                viewModel.addSplitExpense(
                                    tripId,
                                    title.text.toString(),
                                    value,
                                    currency,
                                    listOf(
                                        ExpenseAllocation(sourceId, part),
                                        ExpenseAllocation(
                                            secondSourceId,
                                            value.subtractAmount(part),
                                        ),
                                    ),
                                )
                            else
                                viewModel.addExpense(
                                    tripId,
                                    sourceId,
                                    title.text.toString(),
                                    amount.text.toString(),
                                )
                        }
                    } else
                        Snackbar.make(
                                root,
                                uiText(R.string.choose_or_create_source),
                                Snackbar.LENGTH_LONG,
                            )
                            .show()
                }
            }
        saveExpenseButton = save
        fun render() {
            currencyField.setText(currency)
            manual.visibility = if (automatic) View.GONE else View.VISIBLE
            val selected = sources.firstOrNull { it.id == sourceId && it.currency == currency }
            val second =
                sources.firstOrNull {
                    it.id == secondSourceId && it.currency == currency && it.id != sourceId
                }
            source.setText(selected?.let { "${it.title} · №${it.id}" }.orEmpty())
            val value = number(amount.text.toString())
            val part = number(firstAmount.text.toString())
            val remainder =
                if (value.isFinite() && part.isFinite()) value.subtractAmount(part) else Double.NaN
            splitFields.visibility = if (split) View.VISIBLE else View.GONE
            splitToggle.text =
                if (split) uiText(R.string.disable_split) else uiText(R.string.split_expense)
            secondSource.setText(second?.let { "${it.title} · №${it.id}" }.orEmpty())
            secondAmount.text =
                if (remainder.isFinite() && remainder > 0)
                    uiText(R.string.second_allocation_summary, money(remainder, currency))
                else uiText(R.string.second_allocation_hint)
            caption.text =
                if (automatic) uiText(R.string.automatic_estimate_caption)
                else uiText(R.string.actual_cost_caption)
            retry.visibility =
                if (automatic && rateState is RateUiState.Unavailable) View.VISIBLE else View.GONE
            if (automatic) {
                val snapshot = currentRate()?.rate
                val estimated =
                    snapshot?.let {
                        runCatching { calculator.estimateRubleCost(value, it.rate) }.getOrNull()
                    }
                preview.text =
                    estimated?.let { uiText(R.string.estimated_money, money(it, "RUB")) } ?: "— ₽"
                explanation.text =
                    if (snapshot != null)
                        uiText(
                            R.string.reference_rate_explanation,
                            snapshot.date,
                            if (currentRate()?.isCached == true) uiText(R.string.cached_rate)
                            else "Frankfurter",
                        )
                    else
                        when (val status = rateState) {
                            is RateUiState.Unavailable ->
                                uiText(
                                    R.string.rate_error_manual_alternative,
                                    errorText(status.error),
                                )
                            is RateUiState.Loading ->
                                uiText(R.string.loading_currency_rate, currency)
                            else -> uiText(R.string.choose_rate_currency)
                        }
            } else {
                val cost =
                    runCatching {
                            require(selected != null)
                            if (split) {
                                require(second != null && part > 0 && remainder > 0)
                                calculator.allocateRubleCost(
                                    part,
                                    selected.remainingAmount,
                                    selected.remainingRubles,
                                ) +
                                    calculator.allocateRubleCost(
                                        remainder,
                                        second.remainingAmount,
                                        second.remainingRubles,
                                    )
                            } else
                                calculator.allocateRubleCost(
                                    value,
                                    selected.remainingAmount,
                                    selected.remainingRubles,
                                )
                        }
                        .getOrNull()
                preview.text = cost?.let { money(it, "RUB") } ?: "— ₽"
                explanation.text =
                    if (split)
                        listOfNotNull(selected, second)
                            .joinToString("\n") {
                                uiText(
                                    R.string.source_available_balance,
                                    it.title,
                                    money(it.remainingAmount, it.currency),
                                )
                            }
                            .ifEmpty { uiText(R.string.choose_split_sources) }
                    else
                        selected?.let {
                            uiText(
                                R.string.balance_including_fees,
                                money(it.remainingAmount, it.currency),
                            )
                        } ?: uiText(R.string.choose_source_hint)
            }
        }
        renderExpense = { render() }
        fun refreshRate() {
            if (automatic && currency.isNotEmpty()) viewModel.loadAutomaticRate(currency)
            render()
        }
        modes.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                automatic = id == ID_MODE_AUTOMATIC
                refreshRate()
            }
        }
        amount.doAfterTextChanged { render() }
        firstAmount.doAfterTextChanged {
            firstAmount.error(null)
            render()
        }
        parentFragmentManager.setFragmentResultListener("expense_currency", viewLifecycleOwner) {
            _,
            result ->
            currency = result.getString("code").orEmpty()
            sourceId = 0
            secondSourceId = 0
            refreshRate()
        }
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            if (summary?.trip?.id == tripId) {
                val initial = currency.isEmpty()
                if (initial) currency = summary.trip.currency
                sources = summary.sources
                if (sourceId == 0L)
                    sourceId =
                        sources
                            .firstOrNull { it.currency == currency && it.remainingAmount > 0 }
                            ?.id ?: 0
                if (initial) refreshRate() else render()
            }
        }
        viewModel.automaticRate.observe(viewLifecycleOwner) { value ->
            rateState = value
            render()
        }
        viewModel.expenseSaveState.observe(viewLifecycleOwner) { result ->
            save.isEnabled = result != SaveState.Saving
            when (result) {
                is SaveState.Saved -> {
                    viewModel.acknowledgeExpenseSave()
                    parentFragmentManager.popBackStack()
                }
                is SaveState.Failed -> {
                    Snackbar.make(root, errorText(result.error), Snackbar.LENGTH_LONG).show()
                    viewModel.acknowledgeExpenseSave()
                }
                else -> Unit
            }
        }
        capture = {
            Bundle().apply {
                putString("title", title.text.toString())
                putString("currency", currency)
                putString("amount", amount.text.toString())
                putBoolean("automatic", automatic)
                putLong("source", sourceId)
                putBoolean("split", split)
                putLong("second_source", secondSourceId)
                putString("first_amount", firstAmount.text.toString())
            }
        }
        viewModel.load(tripId)
        if (currency.isNotEmpty()) refreshRate()
        render()
        return root
    }

    private var renderExpense: (() -> Unit)? = null
    private var saveExpenseButton: MaterialButton? = null

    override fun onDestroyView() {
        super.onDestroyView()
        renderExpense = null
        saveExpenseButton = null
    }

    companion object {
        const val ID_SOURCE_SPINNER = 1003
        const val ID_SAVE_EXPENSE = 1004
        const val ID_MODE_AUTOMATIC = 1005
        const val ID_MODE_MANUAL = 1006

        fun newInstance(tripId: Long) =
            AddExpenseFragment().apply { arguments = Bundle().apply { putLong("trip_id", tripId) } }
    }
}
