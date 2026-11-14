package com.Picone.MyCardScan.fragment

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.activity.CardDetailActivity
import com.Picone.MyCardScan.fragment.adapter.SlotAdapter
import com.Picone.MyCardScan.repository.CollectionRepository
import com.Picone.MyCardScan.repository.SavedCard
import com.Picone.MyCardScan.repository.SettingsRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.max


//collezione dell'utente organizzata a pagine
class CollectionFragment : Fragment(R.layout.collection_fragment) {

    private var pageSize = 9
    private var gridSize = 3
    private var cards: List<SavedCard> = emptyList()
    private var currentPage = 0

    private val adapter = SlotAdapter() // Contenitore delle carte nella collezione

    // bottoni per cambiare pagina
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    private lateinit var pageTextView: TextView
    private lateinit var rv: RecyclerView

    private var deleteMode = false
    private val selected = mutableSetOf<String>()   // imagePath delle carte selezionte
    private lateinit var btnTrash: ImageButton // bottone cancella

    private val totalPages: Int
        get() = max(1, (cards.size + pageSize - 1) / pageSize)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext )
        pageTextView = view.findViewById(R.id.tvPage )

        //recuperiamo gridSize dalle impostazioni
        gridSize = SettingsRepository.getGridSize(requireContext())
        pageSize = gridSize * gridSize

        rv = view.findViewById(R.id.rvSlots)
        rv.layoutManager = GridLayoutManager(requireContext(), gridSize)

        adapter.compact = gridSize == 4
        rv.adapter = adapter

        btnTrash = view.findViewById(R.id.btnTrash)
        btnTrash.setOnClickListener { onTrashClick() }
        adapter.onClick = { card ->
            if (deleteMode) toggleSelection(card) else openDetail(card) }
        attachDragAndDrop(rv)

        btnPrev.setOnClickListener { goToPage(currentPage -1) }
        btnNext.setOnClickListener { goToPage(currentPage + 1) }
        render() }



    private fun goToPage(page: Int) {
        currentPage = page.coerceIn(0, totalPages - 1)
        render()
    }

    private fun render() {
        val slots = (0 until pageSize).map { cards.getOrNull(currentPage * pageSize + it) }
        adapter.submit(slots)
        rv.scrollToPosition(0)

        updateDeleteUi()
        btnPrev.visibility = if (currentPage > 0) View.VISIBLE else View.INVISIBLE
        btnNext.visibility = if (currentPage < totalPages - 1) View.VISIBLE else View.INVISIBLE
    }




    override fun onResume() {
        super.onResume()
        applyGridSettings()
        cards = CollectionRepository.getAll(requireContext())
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        render()
    }

    private fun onTrashClick() {
        when { !deleteMode -> { deleteMode = true; updateDeleteUi() }
            selected.isEmpty() -> exitDeleteMode()
            else -> confirmDeleteSelected() }
    }

    private fun toggleSelection(card: SavedCard)
    {   if (!deleteMode) return

        if (!selected.add(card.imagePath)) selected.remove(card.imagePath)
        updateDeleteUi()
    }

    // apre cardDetailActivity
    private fun openDetail(card: SavedCard) {
        startActivity(Intent(requireContext(), CardDetailActivity::class.java)
                .putExtra("name", card.name)
                .putExtra("cardId", card.cardId)
                .putExtra("imagePath", card.imagePath)
                .putExtra("cardmarketUrl", card.cardmarketUrl)
                .putExtra("language", card.language))
    }

    private fun exitDeleteMode()
    {
        deleteMode =  false
        selected.clear()
        updateDeleteUi() }

    private fun confirmDeleteSelected() {
        val n = selected.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.remove_card))
            .setMessage(
                if (n == 1) getString(R.string.remove_card_ask)
                else getString(R.string.remove_ask_n,n))
            .setPositiveButton("Ok") { _, _ ->
                val toDelete = cards.filter { it.imagePath in selected }
                CollectionRepository.removeAll(requireContext(), toDelete)
                cards = CollectionRepository.getAll(requireContext())
                selected.clear()
                deleteMode = false
                currentPage = currentPage.coerceIn(0, totalPages - 1)
                render() }.setNegativeButton(getString(R.string.annull), null).show() }

    private fun updateDeleteUi() {
        adapter.setSelection(deleteMode, selected.toSet())
        btnTrash.setColorFilter(if (selected.isNotEmpty()) Color.parseColor("#FF3B30") else Color.WHITE)

        val pageText = "${currentPage + 1} / $totalPages"
        pageTextView.text = if (deleteMode) "$pageText · ${selected.size} ${getString(R.string.selected)}" else pageText
    }

    // permette di spostare le carte tenendo premuto
    private fun attachDragAndDrop(rv: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            // Gli slot vuoti non si possono trascinare
            override fun getDragDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (adapter.currentSlots()[vh.bindingAdapterPosition] == null) 0
                else super.getDragDirs(rv, vh)

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val to = target.bindingAdapterPosition
                if (adapter.currentSlots()[to] == null) return false   // non si lascia su uno slot vuoto
                adapter.moveItem(vh.bindingAdapterPosition, to)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            // Quando rilasci una carta, salva il nuovo ordine
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                savePageOrder()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }

    private fun savePageOrder() {
        val pageCards = adapter.currentSlots().filterNotNull()
        val start = currentPage * pageSize
        val updated = cards.toMutableList()
        pageCards.forEachIndexed { i, card -> updated[start + i] = card }

        if (updated == cards)
            return   // nessun cambiamento
        cards = updated
        CollectionRepository.saveAll(requireContext(), cards)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    private fun refresh() {
        applyGridSettings()
        cards = CollectionRepository.getAll(requireContext())
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        render()
    }

    // Se la griglia è cambiata nelle impostazioni, aggiorna span e dimensione pagina
    private fun applyGridSettings() {
        val newSize = SettingsRepository.getGridSize(requireContext())
        if (newSize == gridSize) return

        val firstIndex = currentPage * pageSize
        gridSize = newSize
        adapter.compact = newSize == 4
        pageSize = newSize * newSize
        (rv.layoutManager as GridLayoutManager).spanCount = newSize
        currentPage = firstIndex / pageSize
    }
}