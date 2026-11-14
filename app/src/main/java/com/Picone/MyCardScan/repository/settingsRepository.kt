package com.Picone.MyCardScan.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit


enum class AppTheme(val nightMode: Int) {

    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM ) ,

    LIGHT(AppCompatDelegate.MODE_NIGHT_NO ),

    DARK(AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

object SettingsRepository {

    private const val PREFERENCES = "settings"
    private const val GRID_SIZE = "grid_size"
    private const val THEME = "theme"
    private const val FILTER_BY_COUNTRY = "filter_by_country"

    private const val NEARBY_ENABLED = "nearby_enabled"
    private const val DISPLAY_NAME = "display_name"
    private const val SHARE_COLLECTION = "share_collection"



    private fun localPref(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    // Griglia: (3x3) o (4x4)

    fun getGridSize(context: Context):  Int     =
        if ( localPref(context).getInt(GRID_SIZE , 3) == 4 ) 4 else  3


    fun setGridSize(context: Context, size:  Int) {
        localPref(context).edit { putInt(GRID_SIZE, if (size == 4) 4 else 3) }
    }

    fun  getTheme(context: Context): AppTheme=
        AppTheme.fromName(localPref(context).getString(  THEME , null))


    fun setTheme(context: Context, theme: AppTheme ) {

        localPref(context).edit {
            putString(THEME, theme.name)
        }
        AppCompatDelegate.setDefaultNightMode(theme.nightMode )
    }



    fun applyTheme (context: Context) {
        AppCompatDelegate.setDefaultNightMode(getTheme(context ).nightMode)

    }

    // Filtro venditori CardMarket per paese (predefinito: acceso)

    fun getFilterByCountry(context: Context): Boolean =
        localPref(context).getBoolean(FILTER_BY_COUNTRY, true)

    fun setFilterByCountry(context: Context, enabled: Boolean) {
        localPref(context).edit { putBoolean(FILTER_BY_COUNTRY, enabled) }
    }



    fun getNearbyEnabled(c: Context) = localPref(c).getBoolean(NEARBY_ENABLED, false)
    fun setNearbyEnabled(c: Context, on: Boolean) = localPref(c).edit { putBoolean(NEARBY_ENABLED, on) }

    fun getDisplayName(c: Context): String =
        localPref(c).getString(DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: "Collezionista"
    fun setDisplayName(c: Context, name: String) =
        localPref(c).edit { putString(DISPLAY_NAME, name.trim().take(20)) }

    fun getShareCollection(c: Context) = localPref(c).getBoolean(SHARE_COLLECTION, false)
    fun setShareCollection(c: Context, on: Boolean) = localPref(c).edit { putBoolean(SHARE_COLLECTION, on) }

}