package com.Picone.MyCardScan.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

//entita delle carte salvate nel databse != da savedCard, carte reali salvate sul dispositvo nella repository
@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["setCode", "number"], unique = true),
        Index(value = ["number", "setTotal"])
    ]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val setCode: String,   // codice del set 3 caratteri
    val number: Int,       // numero di collezione nel set della carta
    val setTotal: Int? = null,          // carte regolari totali del set(fuorserie escluse)
    val rarity: String? = null,
    val imgPath : String? = null,  // nel caso si voglia implementare le immagini delle carte nel database
    val cardmarketUrl: String? = null

)