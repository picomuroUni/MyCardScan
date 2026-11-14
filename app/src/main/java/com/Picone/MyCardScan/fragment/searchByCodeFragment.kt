package com.Picone.MyCardScan.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.activity.WebViewActivity
import com.Picone.MyCardScan.data.AppDatabase
import com.Picone.MyCardScan.data.CardEntity
import com.Picone.MyCardScan.recognition.CardCode
import com.Picone.MyCardScan.recognition.parseCardCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// cerca una carta nel database interno tramite il codice univoco (setcode+number)

class searchByCodeFragment : Fragment(R.layout.search_by_code_fragment) {

    private lateinit var textField: EditText
    private lateinit var tvResult: TextView
    private lateinit var btnOpen: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textField = view.findViewById(R.id.textField)
        tvResult = view.findViewById(R.id.tvResult)
        btnOpen = view.findViewById(R.id.btnOpen)

        view.findViewById<Button>(R.id.btnSearch).setOnClickListener { search() }
        textField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
    }

    private fun search() {
        btnOpen.visibility = View.GONE
        val parsed = parseCardCode(textField.text.toString())
        if (parsed == null) {
            tvResult.text = getString(R.string.search_by_code_error)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getInstance(requireContext()).cardDao()
            val card = withContext(Dispatchers.IO) {
                dao.findBySetAndNumber(parsed.setCode, parsed.number).firstOrNull()
            }
            showResult(card, parsed)
        }
    }

    private fun showResult(card: CardEntity?, parsed: CardCode) {
        val failed_text= getString(R.string.search_by_code_failed)
        if (card == null) {
            tvResult.text = "${failed_text} ${parsed.setCode} ${parsed.number}"
            return }
        tvResult.text = "${card.name}\n${card.setCode} ${card.number.toString().padStart(3, '0')}"

        val url = card.cardmarketUrl
        if (!url.isNullOrBlank()) {
            btnOpen.visibility = View.VISIBLE
            btnOpen.setOnClickListener {
                startActivity(
                    Intent(requireContext(), WebViewActivity::class.java)
                        .putExtra("baseUrl", url)
                )
            }
        }
    }
}