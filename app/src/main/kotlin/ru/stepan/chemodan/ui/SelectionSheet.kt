package ru.stepan.chemodan.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.stepan.chemodan.R
import ru.stepan.chemodan.data.Catalogue

@AndroidEntryPoint
class SelectionSheet : BottomSheetDialogFragment() {
    @Inject lateinit var catalogue: Catalogue
    private val viewModel: SelectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?,
    ): View {
        val countryMode = requireArguments().getBoolean("country")
        val sourceMode = requireArguments().containsKey("trip_id")
        val requestKey = requireArguments().getString("key")!!
        val body =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    context.dimension(R.dimen.screen_gutter),
                    context.dimension(R.dimen.spacing_large),
                    context.dimension(R.dimen.screen_gutter),
                    context.dimension(R.dimen.spacing_large),
                )
            }
        val header =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        body.addView(header)
        val heading =
            TextView(requireContext()).apply {
                text =
                    if (sourceMode) uiText(R.string.your_exchanges)
                    else if (countryMode) uiText(R.string.choose_country)
                    else uiText(R.string.choose_currency)
                textSize = 24f
                setTextColor(context.themeColor(MaterialR.attr.colorOnSurface))
                includeFontPadding = false
                maxLines = 2
                typeface =
                    android.graphics.Typeface.create(
                        "sans-serif-medium",
                        android.graphics.Typeface.NORMAL,
                    )
            }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(
            androidx.appcompat.widget.AppCompatImageButton(requireContext()).apply {
                contentDescription = uiText(R.string.close_selection)
                setImageResource(R.drawable.ic_close)
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                background = context.rounded(MaterialR.attr.colorPrimaryContainer, 24)
                setOnClickListener { dismiss() }
            },
            LinearLayout.LayoutParams(requireContext().dp(48), requireContext().dp(48)),
        )
        val search =
            body.field(
                uiText(R.string.search),
                if (sourceMode) uiText(R.string.source_search_hint)
                else if (countryMode) uiText(R.string.country_search_hint)
                else uiText(R.string.currency_search_hint),
            )
        var items =
            if (sourceMode) emptyList()
            else if (countryMode)
                catalogue.countries.value.map {
                    Option(
                        it.code,
                        "${it.flag}  ${it.name}",
                        "${catalogue.currency(it.currency)?.name ?: it.currency} · ${it.currency}",
                        it.flag,
                        it.name,
                    )
                }
            else
                catalogue.currencies().map {
                    Option(it.code, "${it.code}  ·  ${it.name}", it.countries, it.code, it.name)
                }
        val result = body.label("", 13f)
        val adapter = OptionsAdapter { option ->
            parentFragmentManager.setFragmentResult(
                requestKey,
                Bundle().apply { putString("code", option.code) },
            )
            dismiss()
        }
        val list =
            RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                clipToPadding = false
                setPadding(0, context.dp(8), 0, context.dp(16))
            }
        body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        fun filter(query: String) {
            val filtered =
                items.filter {
                    "${it.title} ${it.subtitle} ${it.code}"
                        .contains(query.trim(), ignoreCase = true)
                }
            adapter.submitList(filtered)
            result.text = if (filtered.isEmpty()) uiText(R.string.nothing_found) else ""
            result.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
        search.doAfterTextChanged { filter(it.toString()) }
        filter("")
        if (!sourceMode)
            viewLifecycleOwner.lifecycleScope.launch {
                launch { catalogue.refresh() }
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    catalogue.countries.collect {
                        items =
                            if (countryMode)
                                it.map { country ->
                                    Option(
                                        country.code,
                                        "${country.flag}  ${country.name}",
                                        "${catalogue.currency(country.currency)?.name ?: country.currency} · ${country.currency}",
                                        country.flag,
                                        country.name,
                                    )
                                }
                            else
                                catalogue.currencies().map { currency ->
                                    Option(
                                        currency.code,
                                        "${currency.code}  ·  ${currency.name}",
                                        currency.countries,
                                        currency.code,
                                        currency.name,
                                    )
                                }
                        filter(search.text.toString())
                    }
                }
            }
        if (sourceMode)
            viewLifecycleOwner.lifecycleScope.launch {
                result.text = uiText(R.string.loading_exchanges)
                val currency = requireArguments().getString("currency").orEmpty()
                val excludeId = requireArguments().getLong("exclude_id")
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.sources(requireArguments().getLong("trip_id")).collectLatest { summary
                        ->
                        items =
                            summary
                                ?.sources
                                .orEmpty()
                                .filter {
                                    (currency.isEmpty() || it.currency == currency) &&
                                        it.id != excludeId
                                }
                                .map {
                                    Option(
                                        it.id.toString(),
                                        it.title,
                                        "№${it.id} · ${if (it.remainingAmount > 0) uiText(R.string.available) else uiText(R.string.exhausted)} ${money(it.remainingAmount, it.currency)}\n1 ${it.currency} = ${money(it.originalRubles / it.originalAmount, "RUB")}",
                                        it.currency,
                                        it.title,
                                        enabled = it.remainingAmount > 0,
                                    )
                                }
                        filter(search.text.toString())
                        if (items.isEmpty()) {
                            result.text =
                                if (currency.isEmpty()) uiText(R.string.no_trip_exchanges)
                                else uiText(R.string.no_other_currency_source, currency)
                        }
                        val desiredHeight =
                            requireContext().dp(160 + items.size.coerceIn(1, 6) * 88)
                        view?.layoutParams?.height =
                            desiredHeight.coerceAtMost(
                                (resources.displayMetrics.heightPixels * .8).toInt()
                            )
                        view?.requestLayout()
                    }
                }
            }
        return body
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.backgroundTintList =
            ColorStateList.valueOf(requireContext().themeColor(android.R.attr.colorBackground))
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            peekHeight = (resources.displayMetrics.heightPixels * .88).toInt()
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        if (!requireArguments().containsKey("trip_id"))
            view?.layoutParams?.height = (resources.displayMetrics.heightPixels * .88).toInt()
    }

    private data class Option(
        val code: String,
        val title: String,
        val subtitle: String,
        val icon: String,
        val displayTitle: String,
        val enabled: Boolean = true,
    )

    private class OptionsAdapter(val select: (Option) -> Unit) :
        ListAdapter<Option, OptionsAdapter.Holder>(
            object : DiffUtil.ItemCallback<Option>() {
                override fun areItemsTheSame(old: Option, new: Option) = old.code == new.code

                override fun areContentsTheSame(old: Option, new: Option) = old == new
            }
        ) {
        class Holder(val body: LinearLayout) : RecyclerView.ViewHolder(body) {
            val icon =
                TextView(body.context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(context.themeColor(MaterialR.attr.colorPrimary))
                    includeFontPadding = false
                    background = context.rounded(MaterialR.attr.colorPrimaryContainer, 14)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            private val texts =
                LinearLayout(body.context).apply { orientation = LinearLayout.VERTICAL }

            init {
                body.addView(
                    icon,
                    LinearLayout.LayoutParams(body.context.dp(48), body.context.dp(48)).apply {
                        marginEnd = body.context.dp(12)
                    },
                )
                body.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            }

            val title =
                texts.label("", 16f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
            val subtitle =
                texts.label("", 12f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.themeColor(MaterialR.attr.colorOnSurfaceVariant))
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
            Holder(
                LinearLayout(parent.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, context.dp(12), 0, context.dp(12))
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    isFocusable = true
                    val value = android.util.TypedValue()
                    context.theme.resolveAttribute(
                        android.R.attr.selectableItemBackground,
                        value,
                        true,
                    )
                    setBackgroundResource(value.resourceId)
                }
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = getItem(position)
            holder.title.text = item.displayTitle
            holder.icon.text = item.icon
            holder.icon.textSize = if (item.icon.length > 3) 24f else 13f
            holder.subtitle.text = item.subtitle
            holder.body.contentDescription = item.title
            holder.body.isEnabled = item.enabled
            holder.body.alpha = if (item.enabled) 1f else .45f
            holder.body.setOnClickListener { select(item) }
        }
    }

    companion object {
        fun openSources(
            fragment: Fragment,
            key: String,
            tripId: Long,
            currency: String = "",
            excludeId: Long = 0,
        ) {
            if (fragment.parentFragmentManager.findFragmentByTag("selection") != null) return
            SelectionSheet()
                .apply {
                    arguments =
                        Bundle().apply {
                            putString("key", key)
                            putLong("trip_id", tripId)
                            putString("currency", currency)
                            putLong("exclude_id", excludeId)
                        }
                }
                .show(fragment.parentFragmentManager, "selection")
        }

        fun open(fragment: Fragment, key: String, country: Boolean = false) {
            if (fragment.parentFragmentManager.findFragmentByTag("selection") != null) return
            SelectionSheet()
                .apply {
                    arguments =
                        Bundle().apply {
                            putString("key", key)
                            putBoolean("country", country)
                        }
                }
                .show(fragment.parentFragmentManager, "selection")
        }
    }
}
