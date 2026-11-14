package com.Picone.MyCardScan.repository

import kotlinx.serialization.Serializable

@Serializable
data class SavedCard(
    val cardId: String,
    val name: String,
    val number: String,
    val rarity: String?,
    val expansion: String,
    var imagePath: String,
    val cardmarketUrl: String?,
    val language : String = "it",
    val condition: Int? = null      // 1=Mint ... 7=Poor; null = mai scelta dall'utente
)