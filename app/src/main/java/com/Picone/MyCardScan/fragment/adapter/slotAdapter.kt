package com.Picone.MyCardScan.fragment.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.repository.SavedCard
import java.io.File

class SlotAdapter : RecyclerView.Adapter<SlotAdapter.SlotHolder>() {

    private var slots: List<SavedCard?> =  emptyList()
    private var deleteMode =  false
    private var selected: Set<String> =  emptySet()
    var compact = false // per ridurre lo spazio tra le righe
    var onClick: ((SavedCard) -> Unit)? =  null

    fun currentSlots(): List<SavedCard?> =  slots

    fun moveItem(from: Int, to: Int) {
        val list = slots.toMutableList()
        list.add(to, list.removeAt(from))

        slots = list
        notifyItemMoved(from, to)
    }
    fun setSelection(deleteMode: Boolean,    selected: Set<String>) {
        this.deleteMode = deleteMode
        this.selected = selected

        notifyDataSetChanged()
    }



    fun submit( newSlots: List<SavedCard?> ) {
        slots = newSlots

        notifyDataSetChanged()
    }

    class SlotHolder( view: View) :  RecyclerView.ViewHolder(view ) {
        val image: ImageView = view.findViewById(R.id.slotImage)

        val dim: View = view.findViewById(R.id.slotDim)
        val cross: ImageView = view.findViewById(R.id.slotX)

        val name: TextView = view.findViewById(R.id.slotName)

        val code: TextView = view.findViewById(R.id.slotCode)
    }


    override fun onBindViewHolder(holder: SlotHolder, position: Int) {
        val card = slots[position]
        if (card == null) {
            holder.image.setImageDrawable(null)
            holder.itemView.setOnClickListener(null)

            holder.itemView.isClickable = false
        } else {
            holder.name.text = card.name

            holder.code.text = card.cardId
            holder.image.load(File(card.imagePath))

            holder.itemView.setOnClickListener { onClick?.invoke(card) }
        }
        val isSelected = card != null && deleteMode && card.imagePath in selected

        val vis = if (isSelected) View.VISIBLE else View.GONE
        holder.dim.visibility = vis

        holder.cross.visibility = vis

        val d = holder.itemView.resources.displayMetrics.density

        (holder.itemView.layoutParams as
                ViewGroup.MarginLayoutParams).bottomMargin =
            ((if (compact) 4 else 16) * d).toInt()
        (holder.name.layoutParams as
                ViewGroup.MarginLayoutParams).topMargin =
            ((if (compact) 2 else 6) * d).toInt()

        holder.name.textSize = if (compact) 10f else 13f

        holder.code.textSize = if (compact) 8f else 10f
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotHolder {


        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_slot, parent, false)
        return SlotHolder(v)

    }

    override fun getItemCount() = slots.size


}