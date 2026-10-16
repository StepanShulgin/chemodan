package ru.stepan.chemodan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.stepan.chemodan.MainActivity
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue

@AndroidEntryPoint
class TripsFragment : Fragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: TripsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val page =
            requireContext()
                .page(
                    uiText(R.string.trips_title),
                    uiText(R.string.trips_subtitle),
                    listContent = true,
                )
        val (root, body) = page
        val tripsAdapter =
            TripListAdapter(catalogue) { tripId ->
                (requireActivity() as MainActivity).show(
                    TripDetailsFragment.newInstance(tripId),
                    true,
                )
            }
        val empty = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        empty.emptyState(
            "↗",
            uiText(R.string.empty_trips_title),
            uiText(R.string.empty_trips_description),
        )
        body.addView(empty)
        page.list?.adapter = ConcatAdapter(PageHeaderAdapter(body), tripsAdapter)
        root.fixedAction(uiText(R.string.plan_trip), ID_CREATE_TRIP) {
            (requireActivity() as MainActivity).show(CreateTripFragment(), true)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.trips.collect { trips ->
                    empty.visibility = if (trips.isEmpty()) View.VISIBLE else View.GONE
                    tripsAdapter.submitList(trips)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { catalogue.refresh() }
        return root
    }

    companion object {
        const val ID_CREATE_TRIP = 1101
    }
}
