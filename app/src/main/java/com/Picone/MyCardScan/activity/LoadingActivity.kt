package com.Picone.MyCardScan.activity

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.repository.CollectionRepository

import com.Picone.MyCardScan.repository.SavedCard
import com.Picone.MyCardScan.recognition.Scan_Card
import com.Picone.MyCardScan.data.AppDatabase
import com.Picone.MyCardScan.recognition.CountryDetector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import java.io.File
// schermata di caricamento che appare mentre la foto viene analizzata
class LoadingActivity :BaseActivity() {

    private lateinit var messageOnScreen: TextView
    private lateinit var scanner : Scan_Card

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.loading_activity)

        messageOnScreen = findViewById(R.id.messageOnScreen)
        val db = AppDatabase.getInstance(this)
        scanner = Scan_Card(db.cardDao())

        startAnalysis()
    }

    private fun startAnalysis() {
        lifecycleScope.launch {
            try {
                updateStatus(getString(R.string.analysis_loading))
                //l'ultima foto viene salvata come card
                val photoFile = File(filesDir, "card.jpg")
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(photoFile.absolutePath)
                }
                if (bitmap == null) {
                    Log.e("CardRecognizer", "Impossibile leggere ${photoFile.absolutePath}")
                    updateStatus(getString(R.string.analysis_error))
                    delay(2000)
                    finish()
                    return@launch
                }

                val matches = withContext(Dispatchers.Default) { scanner.recognize(bitmap) }

                if (matches.isEmpty()) {
                    updateStatus(getString(R.string.match_not_found))
                    delay(2000)
                    finish()
                } else {
                    updateStatus(getString(R.string.match_found))
                    delay(500)
                    askToSave(matches, photoFile)
                }
            } catch (e: Exception) {
                Log.e("LOADING", "Errore: ${e.message}")
                updateStatus(getString(R.string.analysis_error))
                delay(2000)
                finish()
            }
        }
    }



    private fun askToSave(matches: List<SavedCard>, photoFile: File) {
        when (matches.size) {
            0 -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.match_not_found))
                .setMessage(getString(R.string.analysis_error_message))
                .setPositiveButton("OK", null)
                .show()
            1 -> confirmSave(matches[0], photoFile)
            else -> chooseCard(matches, photoFile)
        }
    }

    private fun confirmSave(card: SavedCard, photoFile: File) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.match_found))
            .setMessage("${card.name}\n${card.expansion} ${card.number}\n\n${getString(R.string.save_to_collection)}")
            .setPositiveButton(getString(R.string.save)) { _, _ -> saveCard(card, photoFile) { askToOpenCardmarket(card) } }
            .setNegativeButton(getString(R.string.save_not)) { _, _ -> askToOpenCardmarket(card) }
            .setCancelable(false)
            .show()
    }

    // Più match
    private fun chooseCard(matches: List<SavedCard>, photoFile: File) {
        val labels = matches.map { "${it.name} · ${it.expansion} ${it.number}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_card))
            .setItems(labels) { _, i -> confirmSave(matches[i], photoFile) }
            .setNegativeButton(getString(R.string.annull), null)
            .show()
    }

    private fun saveCard(card: SavedCard, photoFile: File, onDone: () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(filesDir, "collection").apply { mkdirs() }
                val dest = File(dir, "${card.cardId}.jpg")
                photoFile.copyTo(dest, overwrite = true)
                card.imagePath = dest.absolutePath
                CollectionRepository.add(applicationContext,card)
            }
            Toast.makeText(this@LoadingActivity, getString(R.string.save_confirmed), Toast.LENGTH_SHORT).show()
            onDone()
        }

    }


    //messagio a schermo nel caricamento
    private fun updateStatus(message: String) {
        runOnUiThread {
            messageOnScreen.text = message
        }
    }

    private var pendingCard: SavedCard? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val card = pendingCard ?: run { finish(); return@registerForActivityResult }
        pendingCard = null
        if (granted) openWithFreshCountry(card) else openCardMarket(card)
    }

    private fun askToOpenCardmarket(card: SavedCard) {
        val url = card.cardmarketUrl
        if (url.isNullOrBlank()) {       // carte senza link nel database
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.opening_cm))
            .setMessage(getString(R.string.opening_cm_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> prepareLocationThenOpen(card) }
            .setNegativeButton(getString(R.string.annull)) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun prepareLocationThenOpen(card: SavedCard) {
        when {
            CountryDetector.hasPermission(this) -> openWithFreshCountry(card)
            CountryDetector.askCount(this) >= 2 -> openCardMarket(card)   // non insistere
            else -> askLocationPermission(card)
        }
    }

    // Con il permesso: aggiorna il paese solo se il dato è vecchio, con un tempo massimo
    private fun openWithFreshCountry(card: SavedCard) {
        if (!CountryDetector.isStale(this)) {
            openCardMarket(card)
            return
        }
        lifecycleScope.launch {
            updateStatus(getString(R.string.location_detection_messages))
            withTimeoutOrNull(3_000) { CountryDetector.refresh(applicationContext) }
            openCardMarket(card)
        }
    }

    private fun askLocationPermission(card: SavedCard) {
        CountryDetector.incrementAskCount(this)
        pendingCard = card
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.Location_permission_title))
            .setMessage(getString(R.string.Location_permission_text))
            .setPositiveButton(getString(R.string.consent)) { _, _ ->
                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            .setNegativeButton(getString(R.string.not_now)) { _, _ ->
                pendingCard = null
                openCardMarket(card)          // si apre comunque, senza filtro
            }
            .setCancelable(false)
            .show()
    }

    private fun openCardMarket(card: SavedCard) {
        val url = card.cardmarketUrl ?: run { finish(); return }
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra("baseUrl", url)
                .putExtra("language", card.language)
        )
        finish()
    }


}