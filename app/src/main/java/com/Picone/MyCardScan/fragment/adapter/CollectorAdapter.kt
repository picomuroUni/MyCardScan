package com.Picone.MyCardScan.fragment.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.nearby.NearbyManager

private object CollectorDiff : DiffUtil.ItemCallback<NearbyManager.Collector>() {
    override fun areItemsTheSame(a: NearbyManager.Collector, b: NearbyManager.Collector) = a.id == b.id
    override fun areContentsTheSame(a: NearbyManager.Collector, b: NearbyManager.Collector) = a == b
}

// Lista dei collezionisti trovati vicino: un riquadro per ognuno.
class CollectorAdapter(
    private val onClick: (NearbyManager.Collector) -> Unit
) : ListAdapter<NearbyManager.Collector, CollectorAdapter.Holder>(CollectorDiff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvCollectorName)
        val count: TextView = view.findViewById(R.id.tvCollectorCount)
        val match: TextView = view.findViewById(R.id.tvCollectorMatch)
        val hint: TextView = view.findViewById(R.id.tvCollectorHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_collector, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val c = getItem(position)
        val res = holder.itemView.resources

        holder.name.text = c.name

        holder.count.text = c.collectionCount
            ?.let { n -> res.getQuantityString(R.plurals.collector_cards_count, n, n) }
            ?: res.getString(R.string.collector_loading)

        val m = c.matchCount
        holder.match.visibility = if (m == null) View.GONE else View.VISIBLE
        holder.match.text = when {
            m == null -> ""
            m == 0 -> res.getString(R.string.collector_no_match)
            else -> res.getQuantityString(R.plurals.collector_wanted_match, m, m)
        }
        holder.match.setTypeface(null, if ((m ?: 0) > 0) Typeface.BOLD else Typeface.NORMAL)

        // finché il profilo non è arrivato non si sa se la collezione è condivisa
        holder.hint.visibility = if (c.collectionCount == null) View.GONE else View.VISIBLE
        holder.hint.text = res.getString(
            if (c.collection != null) R.string.collector_tap_to_view else R.string.collector_not_shared
        )

        holder.itemView.setOnClickListener { onClick(c) }
    }
}