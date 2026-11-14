package com.Picone.MyCardScan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


// interfaccia che elenca tutte le operzione permesse sul database dal codice (DAO Data Access Object)
@Dao
interface CardDao {

    @Query("SELECT DISTINCT setCode FROM cards")
    suspend fun getSetCodes(): List<String>

    @Query("SELECT * FROM cards WHERE setCode = :setCode AND number = :number")
    suspend fun findBySetAndNumber(setCode: String, number: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE number = :number AND setTotal = :total")
    suspend fun find(number: Int, total: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE name LIKE '%' || :name || '%'")
    suspend fun findByName(name: String): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun count(): Int

}
