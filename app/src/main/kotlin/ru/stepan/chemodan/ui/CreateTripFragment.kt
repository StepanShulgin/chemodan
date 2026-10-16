package ru.stepan.chemodan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.stepan.chemodan.MainActivity
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue

@AndroidEntryPoint
class CreateTripFragment : StatefulFormFragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: TripsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val tripId = arguments?.takeIf { it.containsKey("trip_id") }?.getLong("trip_id")
        val (root, body) =
            requireContext()
                .page(
                    if (tripId == null) uiText(R.string.create_trip) else uiText(R.string.edit_trip)
                )

        val title =
            body.field(uiText(R.string.trip_name), uiText(R.string.trip_name_example)).apply {
                setText(draft.getString("title", ""))
            }
        var countryCode = draft.getString("country", "").orEmpty()
        var start = draft.getLong("start")
        var end = draft.getLong("end")
        val route = body.section(uiText(R.string.route))
        val countryField =
            route.selector(uiText(R.string.country)) {
                SelectionSheet.open(this, "trip_country", true)
            }
        val dates =
            route.selector(uiText(R.string.trip_dates)) {
                val picker =
                    MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(uiText(R.string.pick_trip_dates))
                        .apply { if (start > 0) setSelection(androidx.core.util.Pair(start, end)) }
                        .build()
                picker.addOnPositiveButtonClickListener { selection ->
                    start = selection.first
                    end = selection.second
                    datesField?.setText(dateRange(start, end))
                    datesField?.error(null)
                }
                picker.show(parentFragmentManager, "trip_dates")
            }
        datesField = dates
        @Suppress("UNCHECKED_CAST")
        (parentFragmentManager.findFragmentByTag("trip_dates")
                as? MaterialDatePicker<androidx.core.util.Pair<Long, Long>>)
            ?.addOnPositiveButtonClickListener { selection ->
                start = selection.first
                end = selection.second
                dates.setText(dateRange(start, end))
            }
        val budgetSection = body.section(uiText(R.string.planned_budget))
        val budget =
            budgetSection.field(uiText(R.string.trip_budget_rubles), "150 000", DECIMAL).apply {
                setText(draft.getString("budget", ""))
                textSize = 28f
            }
        budgetSection
            .label(uiText(R.string.budget_description), 12f)
            .setTextColor(requireContext().themeColor(MaterialR.attr.colorOnSurfaceVariant))
        fun renderCountry() {
            countryField.setText(
                catalogue.country(countryCode)?.let { "${it.flag}  ${it.name}" }.orEmpty()
            )
        }
        renderCountry()
        if (start > 0) dates.setText(dateRange(start, end))
        parentFragmentManager.setFragmentResultListener("trip_country", viewLifecycleOwner) {
            _,
            result ->
            countryCode = result.getString("code").orEmpty()
            renderCountry()
            countryField.error(null)
        }
        val save =
            root.fixedAction(
                if (tripId == null) uiText(R.string.save_new_trip)
                else uiText(R.string.save_changes),
                ID_SAVE_TRIP,
            ) {
                title.error(if (title.text.isNullOrBlank()) uiText(R.string.enter_title) else null)
                countryField.error(
                    if (countryCode.isEmpty()) uiText(R.string.choose_country) else null
                )
                dates.error(if (start == 0L) uiText(R.string.choose_dates) else null)
                budget.error(
                    if (!number(budget.text.toString()).let { it.isFinite() && it > 0 })
                        uiText(R.string.enter_positive_amount)
                    else null
                )
                val country = catalogue.country(countryCode)
                if (
                    !title.text.isNullOrBlank() &&
                        country != null &&
                        start > 0 &&
                        number(budget.text.toString()).let { it.isFinite() && it > 0 }
                ) {
                    saveButton?.isEnabled = false
                    if (tripId == null)
                        viewModel.createTrip(
                            title.text.toString(),
                            country,
                            budget.text.toString(),
                            start,
                            end,
                        )
                    else
                        viewModel.updateTrip(
                            tripId,
                            title.text.toString(),
                            country,
                            budget.text.toString(),
                            start,
                            end,
                        )
                }
            }
        saveButton = save
        capture = {
            Bundle().apply {
                putString("title", title.text.toString())
                putString("country", countryCode)
                putLong("start", start)
                putLong("end", end)
                putString("budget", budget.text.toString())
                putBoolean("loaded", true)
            }
        }
        viewModel.saveState.observe(viewLifecycleOwner) { result ->
            save.isEnabled = result != SaveState.Saving
            when (result) {
                is SaveState.Saved -> {
                    viewModel.acknowledgeSave()
                    if (tripId != null) parentFragmentManager.popBackStack()
                    else (requireActivity() as MainActivity).openCreatedTrip(result.value)
                }
                is SaveState.Failed -> {
                    Snackbar.make(root, errorText(result.error), Snackbar.LENGTH_LONG).show()
                    viewModel.acknowledgeSave()
                }
                else -> Unit
            }
        }
        tripId?.let { id ->
            viewModel.trip.observe(viewLifecycleOwner) { trip ->
                if (trip?.id == id && !draft.getBoolean("loaded")) {
                    title.setText(trip.destination)
                    countryCode = trip.countryCode
                    start = trip.startDate
                    end = trip.endDate
                    budget.setText(if (trip.budget > 0) trip.budget.toString() else "")
                    renderCountry()
                    if (start > 0) dates.setText(dateRange(start, end))
                    draft.putBoolean("loaded", true)
                }
            }
            viewModel.loadTrip(id)
        }
        viewLifecycleOwner.lifecycleScope.launch { catalogue.refresh() }
        return root
    }

    private var datesField: EditText? = null
    private var saveButton: MaterialButton? = null

    override fun onDestroyView() {
        super.onDestroyView()
        datesField = null
        saveButton = null
    }

    companion object {
        const val ID_SAVE_TRIP = 1001

        fun newInstance(tripId: Long) =
            CreateTripFragment().apply { arguments = Bundle().apply { putLong("trip_id", tripId) } }
    }
}
