package ru.stepan.chemodan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.google.android.material.R as MaterialR
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue

@AndroidEntryPoint
class ReferenceRateFragment : StatefulFormFragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: RateViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val (root, body) =
            requireContext()
                .page(uiText(R.string.converter_title), uiText(R.string.reference_rate_subtitle))
        var baseCode = draft.getString("base", "RUB")!!
        var quoteCode = draft.getString("quote", arguments?.getString("currency") ?: "USD")!!
        val amount =
            body
                .field(
                    uiText(R.string.amount),
                    uiText(R.string.converter_amount_example),
                    android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL,
                )
                .apply { setText(draft.getString("amount", "10000")) }
        val base =
            body.selector(uiText(R.string.from_currency)) {
                SelectionSheet.open(this, "converter_base")
            }
        body.action(
            uiText(R.string.swap_currencies),
            {
                val previous = baseCode
                baseCode = quoteCode
                quoteCode = previous
                refreshConverter?.invoke()
            },
            outlined = true,
        )
        val quote =
            body.selector(uiText(R.string.to_currency)) {
                SelectionSheet.open(this, "converter_quote")
            }
        val resultPanel = body.panel(dark = true)
        resultPanel
            .label(uiText(R.string.reference_estimate), 11f)
            .setTextColor(requireContext().themeColor(R.attr.colorHeroForeground))
        val result =
            resultPanel.label(uiText(R.string.loading_rate), 29f).apply {
                setTextColor(context.themeColor(R.attr.colorHeroForeground))
            }
        val meta =
            resultPanel.label("", 13f).apply {
                setTextColor(context.themeColor(R.attr.colorHeroForeground))
            }
        body.space(12)
        body
            .label(uiText(R.string.reference_rate_disclaimer), 13f)
            .setTextColor(requireContext().themeColor(MaterialR.attr.colorOnSurfaceVariant))
        var rateState: RateUiState = viewModel.state.value ?: RateUiState.Idle
        fun render() {
            base.setText(
                uiText(
                    R.string.currency_with_name,
                    baseCode,
                    catalogue.currency(baseCode)?.name.orEmpty(),
                )
            )
            quote.setText(
                uiText(
                    R.string.currency_with_name,
                    quoteCode,
                    catalogue.currency(quoteCode)?.name.orEmpty(),
                )
            )
            when (val status = rateState) {
                is RateUiState.Available ->
                    status.result
                        .takeIf { it.rate.base == baseCode && it.rate.quote == quoteCode }
                        ?.let { snapshot ->
                            val value = number(amount.text.toString())
                            val rate = snapshot.rate
                            result.text =
                                if (value.isFinite() && value >= 0)
                                    money(value * rate.rate, quoteCode)
                                else uiText(R.string.enter_amount)
                            meta.text =
                                uiText(
                                    R.string.converter_rate_details,
                                    baseCode,
                                    rateValue(rate.rate, quoteCode),
                                    rate.date,
                                    if (snapshot.isCached) uiText(R.string.last_saved_rate)
                                    else "Frankfurter",
                                )
                        }
                is RateUiState.Unavailable -> {
                    result.text = uiText(R.string.rate_unavailable)
                    meta.text = errorText(status.error)
                }
                else -> {
                    result.text = uiText(R.string.loading_rate)
                    meta.text = ""
                }
            }
        }
        fun refresh() {
            viewModel.load(baseCode, quoteCode)
        }
        refreshConverter = { refresh() }
        amount.doAfterTextChanged { render() }
        listOf("converter_base" to true, "converter_quote" to false).forEach { (key, isBase) ->
            parentFragmentManager.setFragmentResultListener(key, viewLifecycleOwner) { _, selection
                ->
                if (isBase) baseCode = selection.getString("code")!!
                else quoteCode = selection.getString("code")!!
                refresh()
            }
        }
        body.action(uiText(R.string.refresh_rate), { refresh() }, ID_LOAD_RATE, outlined = true)
        viewModel.state.observe(viewLifecycleOwner) {
            rateState = it
            render()
        }
        capture = {
            Bundle().apply {
                putString("base", baseCode)
                putString("quote", quoteCode)
                putString("amount", amount.text.toString())
            }
        }
        viewModel.loadIfNeeded(baseCode, quoteCode)
        render()
        return root
    }

    private var refreshConverter: (() -> Unit)? = null

    override fun onDestroyView() {
        super.onDestroyView()
        refreshConverter = null
    }

    companion object {
        const val ID_LOAD_RATE = 1105

        fun newInstance(currency: String) =
            ReferenceRateFragment().apply {
                arguments = Bundle().apply { putString("currency", currency) }
            }
    }
}
