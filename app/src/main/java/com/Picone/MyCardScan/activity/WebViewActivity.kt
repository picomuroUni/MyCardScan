package com.Picone.MyCardScan.activity

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.recognition.CountryDetector
import com.Picone.MyCardScan.recognition.SellerCountry

enum class CardLanguage(val label: String, val cardmarketId: Int) {

    // presi da cardmarket
    ITA("it", 5),
    EN("en", 1);

    companion object {
        fun fromName(name: String?): CardLanguage =
            entries.firstOrNull { it.name == name } ?: ITA
    }
}

class WebViewActivity :BaseActivity()  {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.web_view_activity)

        val baseUrl = intent.getStringExtra("baseUrl").orEmpty()
        if (baseUrl.isBlank()) {
            Log.e("WEBVIEW", "Nessun URL ricevuto")
            finish()
            return
        }
        val language = CardLanguage.fromName(intent.getStringExtra("language"))

        val params = mutableMapOf("language" to language.cardmarketId.toString())
        SellerCountry.idFor(CountryDetector.cachedCountry(this))?.let {
            params["sellerCountry"] = it.toString()
        }
        val finalUrl = withParams(baseUrl, params)
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true      // CardMarket ne ha bisogno

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("WEBVIEW", "Caricata: $url")
            }
        }

        Log.d("WEBVIEW", "Apro: $finalUrl")
        webView.loadUrl(finalUrl)                       // avvio del caricamento
    }

    private fun withParams(url: String, params: Map<String, String>): String {
        val uri = url.toUri()
        val builder = uri.buildUpon().clearQuery()

        // copia i parametri esistenti, tranne quelli che sostituiamo
        uri.queryParameterNames
            .filter { it !in params.keys }
            .forEach { name ->
                uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
            }

        params.forEach { (key, value) -> builder.appendQueryParameter(key, value) }
        return builder.build().toString()
    }
}