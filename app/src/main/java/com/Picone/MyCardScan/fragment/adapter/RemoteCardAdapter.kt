package com.Picone.MyCardScan.fragment.adapter

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.nearby.NearbyManager
import java.io.File

// carta della collezione di un altro utente.
data class RemoteCard(
    val key: String,       // es. "PBL 001"
    val name: String,
    val imageSource: Any?,
    val wanted: Boolean    // true se è nella MIA Wanted list
)

class RemoteCardAdapter(
    private val items: List<RemoteCard>,
    private val photoDir: File?
) : RecyclerView.Adapter<RemoteCardAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvName)
        val key: TextView = view.findViewById(R.id.tvKey)
        val border: View = view.findViewById(R.id.wantedBorder)
        var pulse: ObjectAnimator? = null
        val image: ImageView = view.findViewById(R.id.ivCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_remote_card, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        holder.name.text = card.name
        holder.key.text = card.key
        holder.image.dispose()
        val received = photoDir?.let { File(it, NearbyManager.photoName(card.key)) }?.takeIf { it.exists() }
        val src: Any? = received ?: card.imageSource
        if (src == null) holder.image.setImageDrawable(null)
        else holder.image.load(src) { crossfade(true) }


        stopPulse(holder)
        holder.border.visibility = if (card.wanted) View.VISIBLE else View.GONE
        //se una carta è presente nella wantedlist il bordo pulsa in giallo
        if (card.wanted && holder.itemView.isAttachedToWindow) startPulse(holder)
    }

    // L'animazione vive solo mentre la vista è sullo schermo: niente animazioni infinite
    // rimaste attive su viste riciclate o su una schermata chiusa.
    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION && items[pos].wanted) startPulse(holder)
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        stopPulse(holder)
        super.onViewDetachedFromWindow(holder)
    }

    private fun startPulse(holder: Holder) {
        if (holder.pulse != null) return
        holder.pulse = ObjectAnimator.ofFloat(holder.border, View.ALPHA, 0.35f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopPulse(holder: Holder) {
        holder.pulse?.cancel()
        holder.pulse = null
        holder.border.alpha = 1f
    }

    override fun getItemCount() = items.size

    // Chiamata quando arriva la foto di una carta: ridisegna solo quella riga.
    fun onPhoto(key: String) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) notifyItemChanged(i)
    }
}