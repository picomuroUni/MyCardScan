package com.Picone.MyCardScan.data

import com.Picone.MyCardScan.recognition.parseCardCode

// Traduce il codice espansione di Cardmarket (quello salvato nel DB) nell'id set di TCGdex.
object SetMapper {

    private val CmToTcgdex = mapOf(
        "TWM" to "sv06",
        "SFA" to "sv06.5",
        "SCR" to "sv07",
        "SSP" to "sv08",
        "PRE" to "sv08.5",
        "JTG" to "sv09",
        "DRI" to "sv10",
        "BLK" to "sv10.5b",
        "WHT" to "sv10.5w",
        "SVP" to "svp",
        "30C" to "30th",
        "PBL" to "me05",
        "ASC" to "me02.5",
        "POR" to "me03",
        "PFL" to "me02"
    )

    // "PBL" = "me05". Restituisce null se il set non è ancora mappato.
    fun toTcgdex(cardmarketSet: String): String? =
        CmToTcgdex[cardmarketSet.trim().uppercase()]

    // "PBL 001" -> "me05-001" (id di carta TCGdex). Null se il codice non è valido o il set non è mappato.
    fun tcgdexCardId(cardId: String): String? {
        val parsed = parseCardCode(cardId) ?: return null
        val set = toTcgdex(parsed.setCode) ?: return null
        return "$set-${parsed.number.toString().padStart(3, '0')}"
    }
}