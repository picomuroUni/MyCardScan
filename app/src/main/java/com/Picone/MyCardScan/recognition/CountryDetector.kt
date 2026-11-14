package com.Picone.MyCardScan.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import androidx.core.content.edit

object CountryDetector {

    private const val PREFS = "location_prefs"
    private const val KEY_COUNTRY = "country_iso"
    private const val KEY_ASK_COUNT = "ask_count"
    private const val KEY_UPDATED = "country_updated_at"


    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPermission(ctx: Context) = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED


    fun askCount(ctx: Context) = prefs(ctx).getInt(KEY_ASK_COUNT, 0)
    fun incrementAskCount(ctx: Context) =
        prefs(ctx).edit().putInt(KEY_ASK_COUNT, askCount(ctx) + 1).apply()

    // Ultimo paese rilevato (es. "IT"), senza costi: si legge da qui al momento di aprire CardMarket.
    fun cachedCountry(ctx: Context): String? = prefs(ctx).getString(KEY_COUNTRY, null)

    // Aggiorna il paese dalla posizione. Salva solo il codice, mai le coordinate.
    @SuppressLint("MissingPermission")
    suspend fun refresh(ctx: Context): String? {
        if (!hasPermission(ctx)) return cachedCountry(ctx)

        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val location = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }.getOrNull() ?: return cachedCountry(ctx)

        val iso = countryFromLocation(ctx, location) ?: return cachedCountry(ctx)
        prefs(ctx).edit {
            putString(KEY_COUNTRY, iso)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
        }
        Log.d("CountryDetector", "Paese rilevato: $iso")
        return iso
    }

    @Suppress("DEPRECATION")
    private suspend fun countryFromLocation(ctx: Context, loc: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(ctx, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(loc.latitude, loc.longitude, 1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses.firstOrNull()?.countryCode)
                        }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        ?.firstOrNull()?.countryCode
                }.getOrNull()
            }
        }
    }

    // True se il paese non è mai stato rilevato o è più vecchio di 12 ore.
    fun isStale(ctx: Context, maxAgeMs: Long = 12 * 60 * 60 * 1000L): Boolean =
        System.currentTimeMillis() - prefs(ctx).getLong(KEY_UPDATED, 0L) > maxAgeMs
}

object SellerCountry {
    // presi da cardMarket
    private val ids = mapOf(
        "IT" to 17,
        "FR" to 12,
        "DE" to 7,
        "MT" to 22,
        "CH" to 4,
        "GB" to 13
    )
    fun idFor(iso: String?): Int? = iso?.uppercase()?.let { ids[it] }
}