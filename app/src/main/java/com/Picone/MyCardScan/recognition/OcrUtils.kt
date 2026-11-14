package com.Picone.MyCardScan.recognition

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import com.google.mlkit.nl.languageid.LanguageIdentification

private  val languageIdentifier = LanguageIdentification.getClient()
private const val TAG = "CardRecognizer"

// Client ML Kit creato una volta sola
private val textRecognizer =
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

data class CardInfo(
    val number: Int?,
    val total: Int?,
    val setCode: String?
)

// Legge il testo di una fascia orizzontale della carta (frazioni dell'altezza, 0.0 - 1.0).
suspend fun readRegion(card: Bitmap, topFrac: Float, bottomFrac: Float, x_offSet: Int): String {
    val y = (card.height * topFrac).toInt()
    val h = (card.height * (bottomFrac - topFrac)).toInt()
    val strip = Bitmap.createBitmap(card, x_offSet, y, card.width-x_offSet, h)
    return textRecognizer.process(InputImage.fromBitmap(strip, 0)).await().text
}

suspend fun readTopText(card: Bitmap) = readRegion(card, 0.0f, 0.12f,0)
suspend fun readMiddleText(card: Bitmap) = readRegion(card, 0.15f, 0.85f, 0)
suspend fun readBottomText(card: Bitmap) = readRegion(card, 0.88f, 1.0f,0)

fun tokenize(text: String): List<String> =
    text.uppercase().replace(Regex("""\s*/\s*"""), "/").split(Regex("""[\s|,;:]+"""))
        .map { it.trim('.', '-', '_', '(', ')', '[',']')}.filter { it.isNotEmpty() }

// Prima prova: solo cifre vere (la più affidabile)
private val STRICT_NUMBER = Regex("""(?<!\d)(\d{1,3})\s*[/\\|]\s*(\d{1,3})(?!\d)""")

// Seconda prova: ammette lettere simili a cifre (O→0, I/L→1)
private val LOOSE_NUMBER = Regex("""(?<!\d)([0-9OIL]{1,3})\s*[/\\|]\s*([0-9OIL]{1,3})(?!\d)""")

private fun fixDigits(s: String): String =
    s.map { c -> when (c) { 'O' -> '0'; 'I', 'L' -> '1'; else -> c } }.joinToString("")

private fun findNumber(text: String): Pair<Int, Int>? {
    STRICT_NUMBER.find(text)?.let {
        return it.groupValues[1].toInt() to it.groupValues[2].toInt()
    }
    for (m in LOOSE_NUMBER.findAll(text)) {
        val a = m.groupValues[1]
        val b = m.groupValues[2]
        // ogni gruppo deve contenere almeno una cifra vera, per evitare "ILL/OIL"
        if (a.none(Char::isDigit) || b.none(Char::isDigit)) continue
        return fixDigits(a).toInt() to fixDigits(b).toInt()
    }
    return null
}

fun parseNumber(text: String, knownSets: Set<String>): CardInfo {
    val up = text.uppercase()
    Log.d(TAG, "Testo OCR: $up")

    val found = findNumber(up)
    val number = found?.first
    val total = found?.second

    // codici più lunghi per primi; vanno bene anche attaccati a cifre ("001/088PBL")
    val setCode = knownSets
        .sortedByDescending { it.length }
        .firstOrNull { code ->
            Regex("(?<![A-Z])${Regex.escape(code)}(?![A-Z])").containsMatchIn(up)
        }

    return CardInfo(number, total, setCode)
}
// Estrae il nome dalla fascia alta, scartando lo stadio e la parte "xx HP"
fun parseName(text: String): String? {
    val ignore = setOf(
        "BASE", "BASIC", "BASE POKÉMON", "STAGE 1", "STAGE 2", "FASE 1", "FASE 2"
    ) // scritte  sulla della carta da ignorare
    return text.lines()
        .map { it.trim() }
        .filter { it.length >= 3 && it.uppercase() !in ignore }
        .map { it.replace(Regex("""\s*\d+\s*HP.*$""", RegexOption.IGNORE_CASE), "").trim() }
        .firstOrNull { it.isNotEmpty() }
}


data class CardCode(val setCode: String, val number: Int)

fun parseCardCode(input: String): CardCode? {
    val m = Regex("""^([A-Z0-9]+?)[\s\-_]*(\d{1,3})$""")
        .find(input.trim().uppercase()) ?: return null
    return CardCode(m.groupValues[1], m.groupValues[2].toInt())
}

// riconoscimento lingua
suspend fun detectCardLanguage(text: String): String? {

    return try {
        val languageIdentifier = LanguageIdentification.getClient()
        val languageCode = languageIdentifier.identifyLanguage(text).await()
        Log.d(TAG, "Language$languageCode")
        if (languageCode != "und") languageCode else null
    } catch (e: Exception) {
        Log.e(TAG,e.toString(),e)
        null
    }
}