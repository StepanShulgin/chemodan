package ru.stepan.chemodan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue
import ru.stepan.chemodan.data.ExchangeStep

@AndroidEntryPoint
class ExchangeSourceFragment : StatefulFormFragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: TripDetailsViewModel by activityViewModels()
    private val sourceViewModel: SourceViewModel by viewModels()

    private data class StepFields(
        val from: EditText,
        val given: EditText,
        val fee: EditText,
        val to: EditText,
        val received: EditText,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val tripId = requireArguments().getLong("trip_id")
        val defaultCurrency = requireArguments().getString("currency", "USD")!!
        val sourceId = requireArguments().getLong("source_id")
        var loaded = sourceId == 0L || draft.getBoolean("loaded")
        val (root, body) =
            requireContext()
                .page(
                    if (sourceId == 0L) uiText(R.string.new_exchange)
                    else uiText(R.string.edit_exchange)
                )
        val title =
            body.field(uiText(R.string.exchange_name), uiText(R.string.source_name_example)).apply {
                setText(draft.getString("title", ""))
            }
        body
            .label(uiText(R.string.commission_included_note), 12f)
            .setTextColor(requireContext().themeColor(MaterialR.attr.colorOnSurfaceVariant))
        if (sourceId != 0L)
            body
                .label(uiText(R.string.source_recalculation_note), 13f)
                .setTextColor(requireContext().themeColor(MaterialR.attr.colorOnSurfaceVariant))
        val stepContainer =
            LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(stepContainer)
        val steps = mutableListOf<StepFields>()
        fun appendStep(saved: Bundle? = null) {
            val index = steps.size
            val inner = stepContainer.panel()
            inner.label(uiText(R.string.exchange_step, index + 1), 14f)
            val (fromColumn, toColumn) = inner.columns()
            val from =
                fromColumn.selector(
                    uiText(R.string.step_from_currency, index + 1),
                    uiText(R.string.giving),
                ) {}
            from.tag =
                saved?.getString("from")
                    ?: if (index == 0) "RUB" else steps.last().to.tag.toString()
            from.setText(from.tag.toString())
            from.inputLayout?.endIconMode = TextInputLayout.END_ICON_NONE
            from.isClickable = false
            from.textSize = 15f
            val given =
                fromColumn
                    .field(
                        uiText(R.string.amount),
                        uiText(R.string.amount_including_commission),
                        DECIMAL,
                        uiText(R.string.step_given_accessibility, index),
                    )
                    .apply {
                        setText(
                            saved?.getString("given")
                                ?: if (index > 0) steps.last().received.text.toString() else ""
                        )
                    }
            val fee =
                inner
                    .field(
                        uiText(R.string.commission),
                        uiText(R.string.commission_currency_hint),
                        DECIMAL,
                        uiText(R.string.step_fee_accessibility, index),
                    )
                    .apply { setText(saved?.getString("fee", "0") ?: "0") }
            val to =
                toColumn.selector(
                    uiText(R.string.step_to_currency, index + 1),
                    uiText(R.string.receiving),
                ) {
                    SelectionSheet.open(this, "chain_to_${index}")
                }
            to.tag = saved?.getString("to") ?: defaultCurrency
            to.setText(to.tag.toString())
            val received =
                toColumn
                    .field(
                        uiText(R.string.amount),
                        uiText(R.string.amount_after_fees),
                        DECIMAL,
                        uiText(R.string.step_received_accessibility, index),
                    )
                    .apply { setText(saved?.getString("received", "") ?: "") }
            val feeLayout = requireNotNull(fee.inputLayout)
            feeLayout.visibility = if (number(fee.text.toString()) > 0) View.VISIBLE else View.GONE
            val feeToggle = inner.action("", {}, stableId("fee_toggle_${index}"), outlined = true)
            fun renderFeeToggle() {
                feeToggle.text =
                    if (feeLayout.visibility == View.VISIBLE) uiText(R.string.hide_commission)
                    else if (number(fee.text.toString()) > 0)
                        uiText(
                            R.string.commission_summary,
                            money(number(fee.text.toString()), from.tag.toString()),
                        )
                    else uiText(R.string.add_commission)
            }
            feeToggle.setOnClickListener {
                feeLayout.visibility =
                    if (feeLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                renderFeeToggle()
            }
            fee.doAfterTextChanged { renderFeeToggle() }
            renderFeeToggle()
            val rate =
                inner.label("", 12f).apply {
                    setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
                }
            fun renderRate() {
                val paid = number(given.text.toString()) - number(fee.text.toString())
                val output = number(received.text.toString())
                rate.text =
                    if (paid.isFinite() && output.isFinite() && paid > 0 && output > 0)
                        uiText(
                            R.string.effective_rate_preview,
                            from.tag,
                            rateValue(output / paid, to.tag.toString()),
                        )
                    else uiText(R.string.enter_exchange_amounts)
            }
            listOf(given, fee, received).forEach { it.doAfterTextChanged { renderRate() } }
            received.doAfterTextChanged {
                steps.getOrNull(index + 1)?.given?.setText(it.toString())
            }
            parentFragmentManager.setFragmentResultListener(
                "chain_to_${index}",
                viewLifecycleOwner,
            ) { _, result ->
                to.tag = result.getString("code")
                to.setText(to.tag.toString())
                steps.getOrNull(index + 1)?.from?.let { next ->
                    next.tag = to.tag
                    next.setText(to.text)
                }
                renderRate()
            }
            steps += StepFields(from, given, fee, to, received)
            renderRate()
        }
        @Suppress("DEPRECATION") val savedSteps = draft.getParcelableArrayList<Bundle>("steps")
        if (savedSteps.isNullOrEmpty()) appendStep() else savedSteps.forEach { appendStep(it) }
        body.action(
            uiText(R.string.add_exchange_step),
            {
                appendStep()
                removeLastStepButton?.visibility = View.VISIBLE
            },
            outlined = true,
        )
        val removeStep =
            body.action(
                uiText(R.string.remove_last_step),
                {
                    if (steps.size > 1) {
                        steps.removeAt(steps.lastIndex)
                        stepContainer.removeViewAt(stepContainer.childCount - 1)
                    }
                    if (steps.size == 1) removeLastStepButton?.visibility = View.GONE
                },
                outlined = true,
            )
        removeLastStepButton = removeStep
        removeStep.visibility = if (steps.size > 1) View.VISIBLE else View.GONE
        val save =
            root.fixedAction(
                if (sourceId == 0L) uiText(R.string.save_exchange)
                else uiText(R.string.save_and_recalculate),
                ID_SAVE_SOURCE,
            ) {}
        save.isEnabled = loaded
        capture = {
            Bundle().apply {
                putBoolean("loaded", loaded)
                putString("title", title.text.toString())
                putParcelableArrayList(
                    "steps",
                    ArrayList(
                        steps.map { f ->
                            Bundle().apply {
                                putString("from", f.from.tag.toString())
                                putString("given", f.given.text.toString())
                                putString("fee", f.fee.text.toString())
                                putString("to", f.to.tag.toString())
                                putString("received", f.received.text.toString())
                            }
                        }
                    ),
                )
            }
        }
        viewModel.sourceSaveState.observe(viewLifecycleOwner) { result ->
            save.isEnabled = loaded && result != SaveState.Saving
            when (result) {
                is SaveState.Saved -> {
                    viewModel.acknowledgeSourceSave()
                    parentFragmentManager.popBackStack()
                }
                is SaveState.Failed -> {
                    Snackbar.make(root, errorText(result.error), Snackbar.LENGTH_LONG).show()
                    viewModel.acknowledgeSourceSave()
                }
                else -> Unit
            }
        }
        save.setOnClickListener {
            if (!loaded) return@setOnClickListener
            save.isEnabled = false
            val changes =
                steps.map {
                    ExchangeStep(
                        it.from.tag.toString(),
                        number(it.given.text.toString()),
                        number(it.fee.text.toString()),
                        it.to.tag.toString(),
                        number(it.received.text.toString()),
                    )
                }
            if (sourceId == 0L) viewModel.addSource(tripId, title.text.toString(), changes)
            else viewModel.updateSource(tripId, sourceId, title.text.toString(), changes)
        }
        if (!loaded) {
            body.isEnabled = false
            sourceViewModel.state.observe(viewLifecycleOwner) { result ->
                if (loaded) return@observe
                when (result) {
                    is SourceState.Available -> {
                        val (source, storedSteps) = result.details
                        if (source == null || source.tripId != tripId) return@observe
                        title.setText(source.title)
                        steps.clear()
                        stepContainer.removeAllViews()
                        storedSteps.forEach { step ->
                            appendStep(
                                Bundle().apply {
                                    putString("from", step.fromCurrency)
                                    putString("given", step.amountGiven.toString())
                                    putString("fee", step.fee.toString())
                                    putString("to", step.toCurrency)
                                    putString("received", step.amountReceived.toString())
                                }
                            )
                        }
                        if (storedSteps.isEmpty()) {
                            appendStep()
                            Snackbar.make(
                                    root,
                                    uiText(R.string.legacy_source_steps_required),
                                    Snackbar.LENGTH_LONG,
                                )
                                .show()
                        }
                        removeStep.visibility = if (steps.size > 1) View.VISIBLE else View.GONE
                        loaded = true
                        save.isEnabled = viewModel.sourceSaveState.value != SaveState.Saving
                        body.isEnabled = true
                    }
                    is SourceState.Failed ->
                        Snackbar.make(root, errorText(result.error), Snackbar.LENGTH_LONG).show()
                    SourceState.Loading -> Unit
                }
            }
            sourceViewModel.load(sourceId)
        }
        return root
    }

    private var removeLastStepButton: MaterialButton? = null

    override fun onDestroyView() {
        super.onDestroyView()
        removeLastStepButton = null
    }

    companion object {
        const val ID_SAVE_SOURCE = 1002

        fun newInstance(tripId: Long, currency: String, sourceId: Long = 0) =
            ExchangeSourceFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong("trip_id", tripId)
                        putString("currency", currency)
                        putLong("source_id", sourceId)
                    }
            }
    }
}
