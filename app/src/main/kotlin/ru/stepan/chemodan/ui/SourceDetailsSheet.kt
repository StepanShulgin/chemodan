package ru.stepan.chemodan.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.setPadding
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import ru.stepan.chemodan.MainActivity
import ru.stepan.chemodan.R

@AndroidEntryPoint
class SourceDetailsSheet : BottomSheetDialogFragment() {
    private val viewModel: SourceViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val body =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dimension(R.dimen.screen_gutter))
            }
        val scroll = NestedScrollView(requireContext()).apply { addView(body) }
        viewModel.state.observe(viewLifecycleOwner) { result ->
            body.removeAllViews()
            if (result is SourceState.Failed) {
                body.label(errorText(result.error))
                body.action(uiText(R.string.close), { dismiss() })
            }
            if (result !is SourceState.Available) return@observe
            val (source, steps) = result.details
            if (source == null) return@observe
            body.eyebrow(uiText(R.string.source_history_title, source.id))
            body.label(source.title, 24f)
            body.panel(dark = true).apply {
                label(uiText(R.string.available_balance_caption), 11f)
                    .setTextColor(context.themeColor(R.attr.colorHeroForeground))
                label(money(source.remainingAmount, source.currency), 28f)
                    .setTextColor(context.themeColor(R.attr.colorHeroForeground))
                label(
                        uiText(
                            R.string.original_amount_summary,
                            money(source.originalAmount, source.currency),
                        ),
                        12f,
                    )
                    .setTextColor(context.themeColor(R.attr.colorHeroForeground))
            }
            body.space(16)
            body.label(uiText(R.string.exchange_steps), 14f)
            steps.forEach { step ->
                body.panel().apply {
                    label("${step.stepIndex + 1} · ${step.fromCurrency} → ${step.toCurrency}", 14f)
                    label(
                        "${money(step.amountGiven, step.fromCurrency)} → ${money(step.amountReceived, step.toCurrency)}",
                        18f,
                    )
                    val detail =
                        label(
                            uiText(
                                R.string.fee_and_rate_details,
                                money(step.fee, step.fromCurrency),
                                step.fromCurrency,
                                rateValue(step.effectiveRate, step.toCurrency),
                            ),
                            13f,
                        )
                    detail.visibility = View.GONE
                    action(
                        uiText(R.string.show_rate_details),
                        {
                            detail.visibility =
                                if (detail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        },
                        outlined = true,
                    )
                }
            }
            if (steps.isEmpty()) body.card(source.chainDescription)
            body.label(
                uiText(R.string.remaining_ruble_cost, money(source.remainingRubles, "RUB")),
                13f,
            )
            body.action(
                uiText(R.string.edit_exchange),
                {
                    (requireActivity() as MainActivity).show(
                        ExchangeSourceFragment.newInstance(
                            source.tripId,
                            source.currency,
                            source.id,
                        ),
                        true,
                    )
                    dismiss()
                },
            )
            body.action(uiText(R.string.close), { dismiss() }, outlined = true)
        }
        viewModel.load(requireArguments().getLong("source_id"))
        return scroll
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.backgroundTintList =
            ColorStateList.valueOf(requireContext().themeColor(android.R.attr.colorBackground))
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
