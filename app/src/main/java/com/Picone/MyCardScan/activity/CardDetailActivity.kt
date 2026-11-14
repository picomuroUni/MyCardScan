package com.Picone.MyCardScan.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import coil.load
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.data.SetMapper
import com.Picone.MyCardScan.repository.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.tcgdex.sdk.TCGdex
import java.io.File
import java.text.NumberFormat
import java.util.Currency
import kotlin.coroutines.cancellation.CancellationException
import androidx.core.net.toUri

// activity aperta cliccando sulle carte nella collection
class CardDetailActivity : BaseActivity() {

    private var condition: Int? = null
    private companion object {
        const val TAG = "CardDetail"
        const val DEFAULT_CONDITION = 2
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_detail_activity)

        val name = intent.getStringExtra("name").orEmpty()
        val cardId = intent.getStringExtra("cardId").orEmpty()
        val imagePath = intent.getStringExtra("imagePath").orEmpty()
        val url = intent.getStringExtra("cardmarketUrl").orEmpty()
        val language = intent.getStringExtra("language")?.takeIf { it.isNotBlank() } ?: "it"
        val savedRarity = intent.getStringExtra("rarity")?.takeIf { it.isNotBlank() }

        val tvLanguage = findViewById<TextView>(R.id.tvLanguage)
        val tvRarity = findViewById<TextView>(R.id.tvRarity)
        val tvAverage = findViewById<TextView>(R.id.tvAveragePrice)
        val tvLow = findViewById<TextView>(R.id.tvLowPrice)

        findViewById<TextView>(R.id.cardName).text = "$name $cardId"
        findViewById<ImageView>(R.id.cardImage).load(File(imagePath))
        condition = CollectionRepository.getAll(this)
            .firstOrNull { it.imagePath == imagePath }?.condition

        val actvCondition = findViewById<AutoCompleteTextView>(R.id.actvCondition)
        val conditions = resources.getStringArray(R.array.card_conditions)
        actvCondition.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, conditions)
        )
// filter = false: altrimenti il menu mostrerebbe solo la voce selezionata
        actvCondition.setText(conditions[(condition ?: DEFAULT_CONDITION) - 1], false)

// scatta solo quando l'utente sceglie una voce, non con setText
        actvCondition.setOnItemClickListener { _, _, position, _ ->
            condition = position + 1
            CollectionRepository.setCondition(this, imagePath, position + 1)
        }




        findViewById<Button>(R.id.openCardmarket).apply {
            isEnabled = url.isNotBlank()
            setOnClickListener {
                startActivity(
                    Intent(this@CardDetailActivity, WebViewActivity::class.java)
                        .putExtra("baseUrl", withCondition(url))
                        .putExtra("language", language)
                )
            }
        }

        // Dati già noti: subito a schermo, anche offline
        tvLanguage.text = getString(R.string.card_language, language)
        setRarity(tvRarity, savedRarity)
        tvAverage.text = getString(R.string.card_loading)
        tvLow.visibility = View.GONE

        // Rarità e prezzi aggiornati da TCGdex
        lifecycleScope.launch {
            val card = try {
                withContext(Dispatchers.IO) {
                    SetMapper.tcgdexCardId(cardId)
                        ?.let { TCGdex(language.lowercase()).fetchCard(it) }
                }
            } catch (e: CancellationException) {
                throw e                                   // non va ingoiata
            } catch (e: Exception) {
                Log.w(TAG, "fetchCard fallito per $cardId", e)
                null
            }

            setRarity(tvRarity, card?.rarity?.takeIf { it.isNotBlank() } ?: savedRarity)

            val cm = card?.pricing?.cardmarket
            tvAverage.text = priceText(R.string.card_average_price, cm?.avg)
            tvLow.text = priceText(R.string.card_low_price, cm?.low)
            tvLow.visibility = View.VISIBLE
        }
    }

    private fun setRarity(tv: TextView, rarity: String?) {
        tv.visibility = if (rarity == null) View.GONE else View.VISIBLE
        if (rarity != null) tv.text = getString(R.string.card_rarity, rarity)
    }

    // Prezzi di Cardmarket: in euro, formattati secondo la lingua dell'app
    private fun priceText(@StringRes label: Int, value: Number?): String {
        val shown = value?.let {
            NumberFormat.getCurrencyInstance(resources.configuration.locales[0])
                .apply { currency = Currency.getInstance("EUR") }
                .format(it)
        } ?: getString(R.string.card_price_unavailable)
        return getString(label, shown)
    }

    // filtro per condizione della carta su cardmarket
    private fun withCondition(url: String): String {
        val min = condition?.takeIf { it in 1..6 } ?: return url
        return url.toUri().buildUpon()
            .appendQueryParameter("minCondition", min.toString())
            .build().toString()
    }
}