package com.Picone.MyCardScan.activity

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.Picone.MyCardScan.repository.SettingsRepository
//classe madre delle activity, per far ereditare enableEdgeToEdge a tutte le activity
abstract class BaseActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        SettingsRepository.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyInsets()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyInsets()
    }

    //evitiamo che la tastiera copra i campi di inserimento
    private var keyboardVisible = false
    protected open fun onKeyboardVisibilityChanged(visible: Boolean) {}

    private fun applyInsets() {
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // in basso vale il maggiore tra barra di sistema e tastiera
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))

            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (visible != keyboardVisible) {
                keyboardVisible = visible
                onKeyboardVisibilityChanged(visible)
            }
            WindowInsetsCompat.CONSUMED
        }
    }
}