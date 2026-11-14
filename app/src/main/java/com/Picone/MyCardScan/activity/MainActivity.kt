package com.Picone.MyCardScan.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.Picone.MyCardScan.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.Picone.MyCardScan.data.SetMapper
import com.Picone.MyCardScan.repository.SettingsRepository
import com.Picone.MyCardScan.nearby.NearbyManager
import com.Picone.MyCardScan.nearby.NearbyPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.tcgdex.sdk.TCGdex
import java.io.File
import kotlin.collections.emptyList

// main activity ospita 4 fragment : collezzione, searchByCode, trova e Impostazioni
//Scan è un bottone nel menu ma non ha fragment
// Nel caso di scan il workflow è scan=> cameraActivity=> UcropActivity => LoadingActivity => WebViewActivity
class MainActivity : BaseActivity()  {
    var startTime : Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.main_activity)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setupWithNavController(navController)
        bottomNav.itemIconTintList = null

        // scan apre direttamente la camera activity invece di cambiare fragment
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.scan_fragment -> { checkCameraPermissionAndOpen(bottomNav); false }
                R.id.find_collectors_fragment ->
                    if (SettingsRepository.getNearbyEnabled(this)) {
                        NavigationUI.onNavDestinationSelected(item, navController)
                    } else {
                        showFeatureDisabledDialog(bottomNav)
                        false
                    }
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.scan_fragment) checkCameraPermissionAndOpen(bottomNav)
        }

        if (savedInstanceState == null) showIntro(bottomNav)
    }

    //fun da lanciare per aprire la camera
    fun checkCameraPermissionAndOpen(view: View) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        }
    }
    fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraLauncher.launch(intent)
    }

    private val cameraLauncher = registerForActivityResult( ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // se la camera activity ritorna come risultato ok, viene lanciata loadingActivity
        if (result.resultCode == RESULT_OK)
        {    val intent = Intent(this,
            LoadingActivity::class.java)
            startActivity(intent)
        }else{
            Log.e("Camera","Errore CameraLauncher ")
        }
    }

    override fun onKeyboardVisibilityChanged(visible: Boolean) {
        findViewById<View>(R.id.bottom_navigation).isVisible = !visible
    }

    //Piccola intro la prima volta che si apre l'app, porta direttamente a scan
    private fun showIntro(bottomNav: BottomNavigationView) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("intro_shown", false)) return

        // Così non riappare nemmeno se l'utente chiude l app a metà
        prefs.edit { putBoolean("intro_shown", true) }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.welcome))
            .setMessage(getString(R.string.welcome_message))
            .setPositiveButton("OK") { _, _ ->
                bottomNav.selectedItemId = R.id.scan_fragment
            }
            .setNegativeButton(getString(R.string.skip), null)
            .show()
    }
    // dialog che appare se find è disabilitata e si clicca sul fragment
    private fun showFeatureDisabledDialog(bottomNav: BottomNavigationView) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nearby_switch_title))
            .setMessage(getString(R.string.nearvy_settings_notification))
            .setPositiveButton("OK") { _, _ -> bottomNav.selectedItemId = R.id.settings_fragment }
            .setNegativeButton("No") { _, _ -> bottomNav.selectedItemId = R.id.collection_fragment }
            .show()
    }

    override fun onStart() {
        super.onStart()
        if (SettingsRepository.getNearbyEnabled(this) && NearbyPermissions.hasAll(this)) {
            NearbyManager.advertise(this)
        }
    }

    override fun onStop() {
        if (!NearbyManager.keepConnections) NearbyManager.stopAll()
        super.onStop()
    }


}

