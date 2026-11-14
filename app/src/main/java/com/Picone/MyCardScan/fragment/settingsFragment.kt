package com.Picone.MyCardScan.fragment

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.repository.AppTheme
import com.Picone.MyCardScan.recognition.CountryDetector
import com.Picone.MyCardScan.repository.SettingsRepository
import com.Picone.MyCardScan.nearby.NearbyManager
import com.Picone.MyCardScan.nearby.NearbyPermissions
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

class settingsFragment : Fragment(R.layout.settings_fragment) {

    private lateinit var locationSwitch: MaterialSwitch
    private lateinit var nearbySwitch: MaterialSwitch
    private lateinit var shareSwitch: MaterialSwitch
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameEdit: TextInputEditText
    private lateinit var shareRow: View
    private var nameAtStart = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGrid(view)
        setupTheme(view)
        setupAppLanguage(view)
        setupSellerFilter(view)
        setupNearby(view)
    }

    // Dopo il ritorno dalle impostazioni di Android il permesso può essere cambiato
    override fun onResume() {
        super.onResume()
        if (::locationSwitch.isInitialized) syncSellerSwitch()
        if (::nearbySwitch.isInitialized) syncNearbyUi()
    }

    override fun onStop() {
        applyNameChange()
        super.onStop()
    }

    // Griglia
    private fun setupGrid(view: View) {
        val ctx = requireContext()
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleGrid)

        toggle.check(if (SettingsRepository.getGridSize(ctx) == 4) R.id.btnGrid4 else R.id.btnGrid3)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            SettingsRepository.setGridSize(ctx, if (checkedId == R.id.btnGrid4) 4 else 3)
        }
    }

    // Temi
    private fun setupTheme(view: View) {
        val cont = requireContext()
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleTheme)

        toggle.check(
            when (SettingsRepository.getTheme(cont)) {
                AppTheme.SYSTEM -> R.id.btnThemeSystem
                AppTheme.LIGHT -> R.id.btnThemeLight
                AppTheme.DARK -> R.id.btnThemeDark
            }
        )
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.btnThemeLight -> AppTheme.LIGHT
                R.id.btnThemeDark -> AppTheme.DARK
                else -> AppTheme.SYSTEM
            }
            // Salva e applica: l'activity viene ricreata in automatico col nuovo tema
            SettingsRepository.setTheme(cont, theme)
        }
    }


    private fun setupAppLanguage(view: View) {
        val ctx = requireContext()
        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.actvAppLanguage)

        // codice lingua -> etichetta (i nomi delle lingue restano nella loro lingua)
        val languages = listOf(
            "it" to "Italiano",
            "en" to "English"
        )

        dropdown.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_list_item_1, languages.map { it.second })
        )

        // Lingua attuale: quella impostata per l'app, altrimenti quella del sistema
        val currentTag = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: Locale.getDefault().language
        val current = languages.firstOrNull { it.first == currentTag } ?: languages.first { it.first == "en" }

        // filter = false: altrimenti il menu mostrerebbe solo la voce selezionata
        dropdown.setText(current.second, false)

        dropdown.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            val tag = languages.first { it.second == label }.first
            if (tag != currentTag) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }
    }

    // Location per filtro venditori
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ctx = requireContext()
        if (granted) {
            lifecycleScope.launch { CountryDetector.refresh(ctx.applicationContext) }
        } else {
            // permesso negato: il filtro non può funzionare, quindi l'interruttore torna spento
            SettingsRepository.setFilterByCountry(ctx, false)
            locationSwitch.isChecked = false
            // se Android non permette più di chiedere, rimanda alle impostazioni di sistema
            val blocked = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (blocked) showOpenSettingsDialog()
        }
    }

    private fun setupSellerFilter(view: View) {
        locationSwitch = view.findViewById(R.id.switchSellerCountry)
        syncSellerSwitch()

        // setOnClickListener scatta solo quando tocca l'utente, non quando cambia isChecked da codice
        locationSwitch.setOnClickListener { onSellerFilterToggled(locationSwitch.isChecked) }
    }

    // L'interruttore è acceso solo se la scelta è "sì" E il permesso c'è ancora
    private fun syncSellerSwitch() {
        val ctx = requireContext()
        locationSwitch.isChecked =
            SettingsRepository.getFilterByCountry(ctx) && CountryDetector.hasPermission(ctx)
    }

    private fun onSellerFilterToggled(enabled: Boolean) {
        val ctx = requireContext()
        SettingsRepository.setFilterByCountry(ctx, enabled)
        if (!enabled) return

        if (CountryDetector.hasPermission(ctx)) {
            lifecycleScope.launch { CountryDetector.refresh(ctx.applicationContext) }
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    // Collezionisti vicini
    private val nearbyPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val ctx = requireContext()
        if (result.values.all { it }) {
            SettingsRepository.setNearbyEnabled(ctx, true)
        } else {
            SettingsRepository.setNearbyEnabled(ctx, false)
            // se Android non permette più di chiedere, rimanda alle impostazioni di sistema
            val blocked = result.filterValues { !it }.keys.any { !shouldShowRequestPermissionRationale(it) }
            if (blocked) {
                showOpenSettingsDialog(
                    getString(R.string.bluetooth_request)
                )
            }
        }
        syncNearbyUi()
    }

    private fun setupNearby(view: View) {
        nearbySwitch = view.findViewById(R.id.switchNearby)
        shareSwitch = view.findViewById(R.id.switchShareCollection)
        nameLayout = view.findViewById(R.id.tilDisplayName)
        nameEdit = view.findViewById(R.id.etDisplayName)
        shareRow = view.findViewById(R.id.rowShareCollection)

        val ctx = requireContext()
        nameAtStart = SettingsRepository.getDisplayName(ctx)
        nameEdit.setText(nameAtStart)
        nameEdit.doAfterTextChanged { SettingsRepository.setDisplayName(ctx, it?.toString().orEmpty()) }
        syncNearbyUi()

        nearbySwitch.setOnClickListener {
            when {
                !nearbySwitch.isChecked -> {
                    SettingsRepository.setNearbyEnabled(ctx, false)
                    NearbyManager.stopAll()            // smetto subito di essere visibile
                    syncNearbyUi()
                }
                NearbyPermissions.hasAll(ctx) -> {
                    SettingsRepository.setNearbyEnabled(ctx, true)
                    syncNearbyUi()
                }
                else -> nearbyPermissions.launch(NearbyPermissions.required())
            }
        }

        shareSwitch.setOnClickListener {
            if (shareSwitch.isChecked) confirmShare()
            else SettingsRepository.setShareCollection(ctx, false)
        }
    }

    // L'interruttore è acceso solo se la scelta è "sì" E i permessi ci sono ancora
    private fun syncNearbyUi() {
        val ctx = requireContext()
        val enabled = SettingsRepository.getNearbyEnabled(ctx) && NearbyPermissions.hasAll(ctx)

        nearbySwitch.isChecked = enabled
        nameLayout.visibility = if (enabled) View.VISIBLE else View.GONE

        // la condivisione si può attivare solo se la funzione è attiva
        shareSwitch.isEnabled = enabled
        shareRow.alpha = if (enabled) 1f else 0.5f
        shareSwitch.isChecked = SettingsRepository.getShareCollection(ctx)
    }

    private fun confirmShare() {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.share_collection_question))
            .setMessage(getString(R.string.nearby_share_desc))
            .setPositiveButton(getString(R.string.share)) { _, _ -> SettingsRepository.setShareCollection(ctx, true) }
            .setNegativeButton(getString(R.string.annull)) { _, _ -> shareSwitch.isChecked = false }
            .setOnCancelListener { shareSwitch.isChecked = false }
            .show()
    }

    // Il nome annunciato agli altri cambia solo riavviando l'annuncio: lo faccio uscendo dalla schermata
    private fun applyNameChange() {
        if (!::nameEdit.isInitialized) return
        val ctx = requireContext()
        val current = SettingsRepository.getDisplayName(ctx)
        if (current == nameAtStart) return
        nameAtStart = current
        if (SettingsRepository.getNearbyEnabled(ctx) && NearbyPermissions.hasAll(ctx)) {
            NearbyManager.stopAll()
            NearbyManager.advertise(ctx)
        }
    }

    private fun showOpenSettingsDialog(
        message: String = getString(R.string.permission_denied_message)
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.permission_denied))
            .setMessage(message)
            .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null)
                    )
                )
            }
            .setNegativeButton(getString(R.string.annull), null)
            .show()
    }

}