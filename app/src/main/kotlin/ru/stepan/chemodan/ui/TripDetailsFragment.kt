package ru.stepan.chemodan.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.R as MaterialR
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.stepan.chemodan.MainActivity
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue
import ru.stepan.chemodan.data.ExpenseMode
import ru.stepan.chemodan.data.TripSummary
import ru.stepan.chemodan.domain.decimalSum

@AndroidEntryPoint
class TripDetailsFragment : Fragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: TripDetailsViewModel by activityViewModels()
    private var selectedTab = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedTab = state?.getInt("tab") ?: 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", selectedTab)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val tripId = requireArguments().getLong("trip_id")
        val page = requireContext().page("", "", listContent = true)
        val (root, body) = page
        val heading = page.heading
        val description = requireNotNull(page.description).apply { textSize = 14f }
        val hero = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(hero)
        body.space(16)
        val tabs =
            TabLayout(requireContext()).apply {
                setBackgroundColor(context.themeColor(android.R.attr.colorBackground))
                setSelectedTabIndicator(context.rounded(MaterialR.attr.colorPrimaryContainer, 16))
                setSelectedTabIndicatorColor(
                    context.themeColor(MaterialR.attr.colorPrimaryContainer)
                )
                setSelectedTabIndicatorGravity(TabLayout.INDICATOR_GRAVITY_STRETCH)
                isTabIndicatorFullWidth = true
                setTabTextColors(
                    context.themeColor(MaterialR.attr.colorOnSurfaceVariant),
                    context.themeColor(MaterialR.attr.colorPrimary),
                )
                addTab(newTab().setText(uiText(R.string.expenses_tab)))
                addTab(newTab().setText(uiText(R.string.exchanges_tab)))
                getTabAt(selectedTab)?.select()
            }
        body.addView(
            tabs,
            LinearLayout.LayoutParams(-1, requireContext().dimension(R.dimen.action_height)),
        )
        val empty = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(empty)
        val rowsAdapter = SummaryListAdapter { sourceId ->
            SourceDetailsSheet()
                .apply { arguments = Bundle().apply { putLong("source_id", sourceId) } }
                .show(parentFragmentManager, "source_details")
        }
        page.list?.adapter = ConcatAdapter(PageHeaderAdapter(body), rowsAdapter)
        val add =
            root.fixedAction("", ID_ADD_EXPENSE) {
                val trip =
                    viewModel.summary.value?.trip?.takeIf { it.id == tripId } ?: return@fixedAction
                if (selectedTab == 0)
                    (requireActivity() as MainActivity).show(
                        AddExpenseFragment.newInstance(tripId),
                        true,
                    )
                else
                    (requireActivity() as MainActivity).show(
                        ExchangeSourceFragment.newInstance(tripId, trip.currency),
                        true,
                    )
            }
        var renderedTab = selectedTab
        fun renderSection(summary: TripSummary) {
            val tabChanged = renderedTab != selectedTab
            renderedTab = selectedTab
            empty.removeAllViews()
            add.text =
                if (selectedTab == 0) uiText(R.string.add_expense) else uiText(R.string.add_source)
            add.id = if (selectedTab == 0) ID_ADD_EXPENSE else ID_ADD_SOURCE
            val rows =
                if (selectedTab == 0) {
                    if (summary.expenses.isEmpty())
                        empty.emptyState(
                            "↗",
                            uiText(R.string.empty_expenses_title),
                            uiText(R.string.empty_expenses_description),
                        )
                    summary.expenses
                        .groupBy { it.groupId ?: "expense_${it.id}" }
                        .map { (key, parts) ->
                            val expense = parts.first()
                            val automatic = expense.calculationMode == ExpenseMode.AUTOMATIC_RATE
                            SummaryListRow(
                                key,
                                "↗",
                                expense.title,
                                if (automatic)
                                    uiText(
                                        R.string.expense_reference_date,
                                        expense.referenceRateDate,
                                    )
                                else if (parts.size > 1)
                                    parts.joinToString("\n") { part ->
                                        val name =
                                            summary.sources
                                                .firstOrNull { it.id == part.sourceId }
                                                ?.title ?: uiText(R.string.exchange_label)
                                        "${name} · №${part.sourceId}: ${money(part.amount, part.currency)}"
                                    }
                                else uiText(R.string.manual_exchange),
                                money(parts.map { it.amount }.decimalSum(), expense.currency),
                                "${if (automatic) "≈ " else ""}${money(parts.map { it.rubleCost }.decimalSum(), "RUB")}",
                            )
                        }
                } else {
                    if (summary.sources.isEmpty())
                        empty.emptyState(
                            "⇄",
                            uiText(R.string.empty_sources_title),
                            uiText(R.string.empty_sources_description),
                        )
                    summary.sources.map { source ->
                        SummaryListRow(
                            "source_${source.id}",
                            "⇄",
                            source.title,
                            uiText(R.string.source_currency_summary, source.id, source.currency),
                            money(source.remainingAmount, source.currency),
                            "1 ${source.currency} = ${money(source.originalRubles / source.originalAmount, "RUB")}",
                            source.id,
                        )
                    }
                }
            rowsAdapter.submitList(rows) { if (tabChanged) page.list?.scrollToPosition(0) }
        }
        tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedTab = tab.position
                    viewModel.summary.value
                        ?.takeIf { it.trip.id == tripId }
                        ?.let { renderSection(it) }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            if (summary?.trip?.id != tripId) return@observe
            val trip = summary.trip
            heading.text = trip.destination
            val country = catalogue.country(trip.countryCode)
            val dates = dateRange(trip.startDate, trip.endDate)
            description.text =
                if (country == null) dates
                else
                    uiText(
                        R.string.country_and_dates,
                        uiText(R.string.country_with_flag, country.flag, country.name),
                        dates,
                    )
            hero.removeAllViews()
            if (trip.legacyBudget != null) {
                hero.card(
                    uiText(R.string.legacy_budget_note, money(trip.legacyBudget, trip.currency))
                )
            } else {
                val budget = viewModel.budgetStatus(trip.budget, summary.spent)
                hero.panel(dark = true).apply {
                    label(
                            if (budget.remaining < 0) uiText(R.string.over_budget_caption)
                            else uiText(R.string.remaining_budget_caption),
                            11f,
                        )
                        .apply {
                            letterSpacing = .08f
                            setTextColor(context.themeColor(R.attr.colorHeroForeground))
                        }
                    space(8)
                    label(money(kotlin.math.abs(budget.remaining), "RUB"), 32f).apply {
                        setTextColor(context.themeColor(R.attr.colorHeroForeground))
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    }
                    space(12)
                    addView(
                        com.google.android.material.progressindicator
                            .LinearProgressIndicator(context)
                            .apply {
                                isIndeterminate = false
                                max = 1000
                                progress = (budget.progress * 1000).toInt()
                                trackCornerRadius = context.dp(4)
                                trackThickness = context.dp(6)
                                setIndicatorColor(context.themeColor(MaterialR.attr.colorSecondary))
                                trackColor = context.themeColor(MaterialR.attr.colorPrimary)
                            },
                        LinearLayout.LayoutParams(-1, context.dp(6)),
                    )
                    space(12)
                    label(
                            uiText(
                                R.string.spending_summary,
                                money(summary.spent, "RUB"),
                                money(trip.budget, "RUB"),
                            ),
                            12f,
                        )
                        .setTextColor(context.themeColor(R.attr.colorHeroForeground))
                    if (summary.estimatedRubles > 0)
                        label(
                                uiText(
                                    R.string.reference_spending_summary,
                                    money(summary.estimatedRubles, "RUB"),
                                ),
                                12f,
                            )
                            .setTextColor(context.themeColor(R.attr.colorHeroForeground))
                }
            }
            renderSection(summary)
        }
        viewModel.load(tripId)
        return root
    }

    companion object {
        const val ID_ADD_SOURCE = 1103
        const val ID_ADD_EXPENSE = 1104

        fun newInstance(id: Long) =
            TripDetailsFragment().apply { arguments = Bundle().apply { putLong("trip_id", id) } }
    }
}
