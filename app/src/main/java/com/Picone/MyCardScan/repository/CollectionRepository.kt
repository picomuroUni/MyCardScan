package com.Picone.MyCardScan.repository


import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
// le carte scannerizzate vengono salvate in json sul dispositivo con lingua e imgPath
object CollectionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.filesDir, "collection.json")

    @Synchronized
    fun getAll(context: Context): List<SavedCard> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            json.decodeFromString<List<SavedCard>>(f.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, card: SavedCard) {
        val updated = getAll(context) + card
        file(context).writeText(json.encodeToString(updated))
    }

    @Synchronized
    fun remove(context: Context, card: SavedCard) {
        val updated = getAll(context).filter { it.imagePath != card.imagePath }
        file(context).writeText(json.encodeToString(updated))
        File(card.imagePath).delete()
    }

    @Synchronized
    fun removeAll(context: Context, toRemove: List<SavedCard>) {
        val paths = toRemove.map { it.imagePath }.toSet()
        val updated = getAll(context).filter { it.imagePath !in paths }
        file(context).writeText(json.encodeToString(updated))
        paths.forEach { File(it).delete() }   // prima il JSON, poi le foto
    }

    @Synchronized
    fun saveAll(context: Context, cards: List<SavedCard>) {
        file(context).writeText(json.encodeToString(cards))
    }

    @Synchronized
    fun setCondition(context: Context, imagePath: String, condition: Int) {
        val updated = getAll(context).map {
            if (it.imagePath == imagePath) it.copy(condition = condition) else it
        }
        file(context).writeText(json.encodeToString(updated))
    }
}