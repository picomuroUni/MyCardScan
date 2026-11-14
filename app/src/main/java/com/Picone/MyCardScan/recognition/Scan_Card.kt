package com.Picone.MyCardScan.recognition

import android.graphics.Bitmap
import android.util.Log
import com.Picone.MyCardScan.data.CardDao
import com.Picone.MyCardScan.repository.SavedCard

import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Collections.emptyList

class Scan_Card(private val dao: CardDao) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var knownSets: Set<String> = emptySet()

    companion object { private const val TAG = "CardRecognizer" }

    suspend fun recognize(bitmap: Bitmap): List<SavedCard> {
        Log.d(TAG, "Bitmap: ${bitmap.width}x${bitmap.height}")

        if (knownSets.isEmpty()) {
            knownSets = dao.getSetCodes().map { it.uppercase() }.toSet()
            Log.d(TAG, "Codici espansione nel DB: ${knownSets.size}")
        }

        // Fascia alta: nome
        val topText = readTopText(bitmap)
        Log.d(TAG, "OCR alto: \"${topText.replace("\n", " | ")}\"")
        Log.d(TAG, "Nome estratto: ${parseName(topText)}")

        // Fascia bassa: numero ed espansione
        val bottomText = readBottomText(bitmap)
        Log.d(TAG, "OCR basso: \"${bottomText.replace("\n", " | ")}\"")
        val info = parseNumber(bottomText, knownSets)
        Log.d(TAG, "Info: $info")

        val number = info.number
        val total = info.total
        val code = info.setCode

        val bySet = if (code != null && number != null) dao.findBySetAndNumber(code, number) else null
        val byTotal = if (number != null && total != null) dao.find(number, total) else null
        Log.d(TAG, "Info=$info | bySetNum=${bySet?.size} byNumTotal=${byTotal?.size}")

        val DaoMatches = when {
            !bySet.isNullOrEmpty() -> bySet
            !byTotal.isNullOrEmpty() -> byTotal
            else -> emptyList()
        }
        Log.d(TAG, "daomatch" + DaoMatches.toString())

        var middleText = readMiddleText(bitmap)
        var language = detectCardLanguage(middleText)
        val resultList = mutableListOf<SavedCard>()
        for(match in DaoMatches){
            val savedCard = SavedCard(
                cardId = match.setCode+match.number.toString(),
                name = match.name,
                number = match.number.toString(),
                rarity = match.rarity,
                expansion = match.setCode,
                imagePath = "",
                cardmarketUrl = match.cardmarketUrl,
                language = language ?: "it"
            )
            resultList.add(savedCard)
        }


        Log.d(TAG, "Match nel DB: ${resultList.size}")
        resultList.forEach { Log.d(TAG, " -> $it") }
        return resultList
    }
}