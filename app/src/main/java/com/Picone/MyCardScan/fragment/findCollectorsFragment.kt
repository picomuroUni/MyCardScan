package com.Picone.MyCardScan.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.activity.NearbyCollectorCollectionActivity
import com.Picone.MyCardScan.data.AppDatabase
import com.Picone.MyCardScan.fragment.adapter.CollectorAdapter
import com.Picone.MyCardScan.repository.SettingsRepository
import com.Picone.MyCardScan.repository.WantedRepository
import com.Picone.MyCardScan.nearby.NearbyManager
import com.Picone.MyCardScan.nearby.NearbyPermissions
import com.Picone.MyCardScan.recognition.parseCardCode
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// tempo massimo per la ricerca, per evitare di sprecare batteria
private const val SEARCH_TIMEOUT_MS = 90_000L


//trova Collezionisti vicini
class findCollectorsFragment : Fragment(R.layout.find_collectors_fragment) {

    private enum class State { DISABLED, NEED_PERMISSION, SEARCHING, FINISHED }

    private lateinit var etWanted: EditText
    private lateinit var tvWantedMsg: TextView
    private lateinit var tvWantedEmpty: TextView
    private lateinit var chipWanted: ChipGroup
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: Button

    private val adapter = CollectorAdapter { openCollection(it) }

    private var state = State.DISABLED
    private var found: List<NearbyManager.Collector> = emptyList()
    private var askedPermissions = false      // si chiede automaticamente una volta sola per apertura
    private var timeoutJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (view == null) return@registerForActivityResult
        if (result.values.all { it }) startSearching() else setState(State.NEED_PERMISSION)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etWanted = view.findViewById(R.id.etWanted)
        tvWantedMsg = view.findViewById(R.id.tvWantedMsg)
        tvWantedEmpty = view.findViewById(R.id.tvWantedEmpty)
        chipWanted = view.findViewById(R.id.chipWanted)
        tvStatus = view.findViewById(R.id.tvStatus)
        btnAction = view.findViewById(R.id.btnAction)

        val rv = view.findViewById<RecyclerView>(R.id.rvCollectors)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.isNestedScrollingEnabled = false       // scorre insieme a tutta la schermata

        view.findViewById<Button>(R.id.btnAddWanted).setOnClickListener { addWanted() }
        etWanted.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addWanted(); true } else false
        }
        renderWanted()

        // La lista si aggiorna da sola ogni volta che NearbyManager trova o aggiorna qualcuno
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NearbyManager.collectors.collect { map ->
                    found = map.values.toList()
                    adapter.submitList(found.sortedByDescending { it.matchCount ?: -1 })
                    renderStatus()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        NearbyManager.keepConnections = false
        startSearchingIfAllowed()
    }

    override fun onStop() {
        timeoutJob?.cancel()
        if (NearbyManager.keepConnections) NearbyManager.stopScanning()   // collezione aperta: tengo le connessioni
        else NearbyManager.reset()
        super.onStop()
    }

    //  Ricerca

    private fun startSearchingIfAllowed() {
        val ctx = requireContext()
        when {
            !SettingsRepository.getNearbyEnabled(ctx) -> setState(State.DISABLED)
            NearbyPermissions.hasAll(ctx) -> startSearching()
            !askedPermissions -> {
                askedPermissions = true
                permissionLauncher.launch(NearbyPermissions.required())
            }
            else -> setState(State.NEED_PERMISSION)
        }
    }

    private fun startSearching() {
        val appCtx = requireContext().applicationContext
        NearbyManager.advertise(appCtx)       // anche io devo essere visibile agli altri
        NearbyManager.discover(appCtx)
        setState(State.SEARCHING)

        timeoutJob?.cancel()
        timeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SEARCH_TIMEOUT_MS)
            NearbyManager.stopScanning()      // smette di cercare, ma tiene i collezionisti già trovati
            setState(State.FINISHED)
        }
    }

    // Se la Wanted list cambia, i conteggi vanno ricalcolati: si riparte con una nuova ricerca
    private fun restartSearchIfActive() {
        if (state != State.SEARCHING) return
        NearbyManager.reset()
        startSearching()
    }

    private fun setState(newState: State) {
        state = newState
        renderStatus()
    }

    private fun renderStatus() {
        val n = found.size
        when (state) {
            State.DISABLED -> showStatus(getString(R.string.status_disabled))
            State.NEED_PERMISSION -> showStatus(
                getString(R.string.status_need_permission),
                getString(R.string.action_open_settings)
            ) { openAppSettings() }
            State.SEARCHING -> showStatus(
                if (n == 0) getString(R.string.status_searching_none)
                else getString(R.string.status_searching_count, n)
            )
            State.FINISHED -> showStatus(
                if (n == 0) getString(R.string.status_finished_none)
                else getString(R.string.status_finished_count, n),
                getString(R.string.action_search_again)
            ) { startSearchingIfAllowed() }
        }
    }

    private fun showStatus(text: String, buttonLabel: String? = null, action: () -> Unit = {}) {
        tvStatus.text = text
        btnAction.visibility = if (buttonLabel == null) View.GONE else View.VISIBLE
        btnAction.text = buttonLabel
        btnAction.setOnClickListener { action() }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null)
            )
        )
    }

    private fun openCollection(collector: NearbyManager.Collector) {
        val collection = collector.collection
        if (collection == null) {
            val msg = if (collector.collectionCount == null) getString(R.string.collector_data_loading)
            else getString(R.string.collector_collection_not_shared, collector.name)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            return
        }
        NearbyManager.keepConnections = true      // le connessioni restano aperte mentre guardo la collezione
        startActivity(
            Intent(requireContext(), NearbyCollectorCollectionActivity::class.java)
                .putExtra("id", collector.id)
                .putExtra("name", collector.name)
                .putStringArrayListExtra("collection", ArrayList(collection))
        )
    }

    //  Wanted list

    private fun addWanted() {
        val parsed = parseCardCode(etWanted.text.toString())
        if (parsed == null) {
            tvWantedMsg.text = getString(R.string.wanted_invalid_code)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = requireContext()
            val card = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(ctx).cardDao()
                    .findBySetAndNumber(parsed.setCode, parsed.number).firstOrNull()
            }
            when {
                card == null ->
                    tvWantedMsg.text = ctx.getString(R.string.wanted_not_found, parsed.setCode, parsed.number)
                WantedRepository.addFrom(ctx, card) -> {
                    tvWantedMsg.text = ctx.getString(R.string.wanted_added, card.name)
                    etWanted.text.clear()
                    renderWanted()
                    restartSearchIfActive()
                }
                else -> tvWantedMsg.text = ctx.getString(R.string.wanted_already_in_list, card.name)
            }
        }
    }

    private fun renderWanted() {
        val ctx = requireContext()
        val list = WantedRepository.getAll(ctx)

        chipWanted.removeAllViews()
        tvWantedEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        list.forEach { w ->
            chipWanted.addView(Chip(ctx).apply {
                text = "${w.name} · ${w.key}"
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    WantedRepository.remove(ctx, w.key)
                    renderWanted()
                    restartSearchIfActive()
                }
            })
        }
    }
}