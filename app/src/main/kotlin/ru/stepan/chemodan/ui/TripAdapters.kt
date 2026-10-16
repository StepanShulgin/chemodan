package ru.stepan.chemodan.ui

import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue
import ru.stepan.chemodan.data.TripEntity

internal class PageHeaderAdapter(private val header: View) :
    RecyclerView.Adapter<PageHeaderAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(header.apply { layoutParams = RecyclerView.LayoutParams(-1, -2) })

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit
}

internal class TripListAdapter(
    private val catalogue: Catalogue,
    private val select: (Long) -> Unit,
) :
    ListAdapter<TripEntity, TripListAdapter.Holder>(
        object : DiffUtil.ItemCallback<TripEntity>() {
            override fun areItemsTheSame(oldItem: TripEntity, newItem: TripEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TripEntity, newItem: TripEntity) =
                oldItem == newItem
        }
    ) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    class Holder(val body: LinearLayout) : RecyclerView.ViewHolder(body) {
        val inner = body.panel()
        val country = inner.eyebrow("")
        val title =
            inner.label("", 23f).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
        val dates =
            inner.label("", 13f).apply {
                setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
            }
        val budget =
            inner.label("", 15f).apply {
                setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(rowContainer(parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val trip = getItem(position)
        val country = catalogue.country(trip.countryCode)
        holder.country.text =
            holder.body.uiText(
                R.string.country_with_flag,
                country?.flag ?: "✈",
                country?.name ?: holder.body.uiText(R.string.trip_badge),
            )
        holder.title.text = trip.destination
        holder.dates.text = holder.body.context.dateRange(trip.startDate, trip.endDate)
        holder.budget.text =
            if (trip.legacyBudget != null) holder.body.uiText(R.string.set_ruble_budget)
            else holder.body.uiText(R.string.budget_summary, money(trip.budget, "RUB"))
        (holder.inner.parent as View).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { select(trip.id) }
        }
    }
}

internal data class SummaryListRow(
    val key: String,
    val symbol: String,
    val title: String,
    val subtitle: String,
    val value: String,
    val secondary: String,
    val sourceId: Long? = null,
)

internal class SummaryListAdapter(private val select: (Long) -> Unit) :
    ListAdapter<SummaryListRow, SummaryListAdapter.Holder>(
        object : DiffUtil.ItemCallback<SummaryListRow>() {
            override fun areItemsTheSame(oldItem: SummaryListRow, newItem: SummaryListRow) =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: SummaryListRow, newItem: SummaryListRow) =
                oldItem == newItem
        }
    ) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    class Holder(val body: LinearLayout) : RecyclerView.ViewHolder(body) {
        val views = body.summaryRow("", "", "", "", "")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(rowContainer(parent))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)
        holder.views.apply {
            symbol.text = row.symbol
            title.text = row.title
            subtitle.text = row.subtitle
            multilineSubtitle.text = row.subtitle
            subtitle.visibility = if ('\n' in row.subtitle) View.GONE else View.VISIBLE
            multilineSubtitle.visibility = if ('\n' in row.subtitle) View.VISIBLE else View.GONE
            value.text = row.value
            secondary.text = row.secondary
            card.setOnClickListener(
                if (row.sourceId != null) View.OnClickListener { select(row.sourceId) } else null
            )
            card.isClickable = row.sourceId != null
            card.isFocusable = row.sourceId != null
        }
    }
}

private fun rowContainer(parent: ViewGroup) =
    LinearLayout(parent.context).apply {
        orientation = LinearLayout.VERTICAL
        val gutter = context.dimension(R.dimen.screen_gutter)
        setPadding(gutter, 0, gutter, 0)
        layoutParams = RecyclerView.LayoutParams(-1, -2)
    }
