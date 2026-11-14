package com.Picone.MyCardScan.repository

import android.content.Context
import com.Picone.MyCardScan.data.CardEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// il codice delle carteè uguale per tutti i telefoni: "PBL 001".


fun cardKey(setCode: String, number: Int): String =
    "${setCode.trim().uppercase()} ${number.toString().padStart(3, '0')}"

private val KEY_REGEX = Regex("""^([A-Z0-9]+?)[\s\-_]*(\d{1,3})$""")


fun normalizeKey(raw: String): String {
    val up = raw.trim().uppercase()
    val m = KEY_REGEX.find(up) ?: return up
    return cardKey(m.groupValues[1], m.groupValues[2].toInt())
}

@Serializable
data class WantedCard(
    val key: String,
    val name: String
)

object WantedRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.filesDir, "wanted.json")

    @Synchronized
    fun getAll(context: Context): List<WantedCard> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString<List<WantedCard>>(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun contains(context: Context, key: String): Boolean {
        val k = normalizeKey(key)
        return getAll(context).any { it.key == k }
    }

    // Aggiunge la carta. Restituisce false se era già nella Wanted list.
    @Synchronized
    fun add(context: Context, card: WantedCard): Boolean {
        val normalized = card.copy(key = normalizeKey(card.key))
        val current = getAll(context)
        if (current.any { it.key == normalized.key }) return false
        file(context).writeText(json.encodeToString(current + normalized))
        return true
    }

    fun addFrom(context: Context, card: CardEntity): Boolean =
        add(context, WantedCard(key = cardKey(card.setCode, card.number), name = card.name))

    @Synchronized
    fun remove(context: Context, key: String) {
        val k = normalizeKey(key)
        val updated = getAll(context).filter { it.key != k }
        file(context).writeText(json.encodeToString(updated))
    }
}