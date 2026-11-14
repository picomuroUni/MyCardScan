package com.Picone.MyCardScan.activity

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Picone.MyCardScan.R
import com.Picone.MyCardScan.data.AppDatabase
import com.Picone.MyCardScan.fragment.adapter.RemoteCard
import com.Picone.MyCardScan.fragment.adapter.RemoteCardAdapter
import com.Picone.MyCardScan.nearby.NearbyManager
import com.Picone.MyCardScan.repository.WantedRepository
import com.Picone.MyCardScan.repository.normalizeKey
import com.Picone.MyCardScan.recognition.parseCardCode
import com.Picone.MyCardScan.repository.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// collezione di un utente vicino trovato con find collectors
class NearbyCollectorCollectionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nearby_collector_collection_activity)

        val name = intent.getStringExtra("name").orEmpty()
        val keys = intent.getStringArrayListExtra("collection").orEmpty()
        val collectorId = intent.getStringExtra("id").orEmpty()

        findViewById<TextView>(R.id.tvTitle).text = "Collezione di $name"

        val rv = findViewById<RecyclerView>(R.id.rvRemote)
        rv.layoutManager = GridLayoutManager(this, 2)

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { buildItems(keys) }
            val adapter = RemoteCardAdapter(items, NearbyManager.photoDir(applicationContext, collectorId))
            rv.adapter = adapter
            NearbyManager.photoReceived.collect { (id, key) ->
                if (id == collectorId) adapter.onPhoto(key)
            }
        }
    }

    private suspend fun buildItems(keys: List<String>): List<RemoteCard> {
        val dao = AppDatabase.getInstance(this).cardDao()
        val wanted = WantedRepository.getAll(this).map { it.key }.toSet()

        // le mie foto, per le carte che possiedo anch'io: chiave canonica -> file
        val myPhotos: Map<String, File> = CollectionRepository.getAll(this)
            .associate { normalizeKey(it.cardId) to File(it.imagePath) }
            .filterValues { it.exists() }

        return keys.take(2000).map { raw ->
            val key = normalizeKey(raw)
            val card = parseCardCode(key)?.let {
                dao.findBySetAndNumber(it.setCode, it.number).firstOrNull()
            }
            RemoteCard(
                key = key,
                name = card?.name ?: key,
                // priorità: immagine di catalogo dal DB (quando ci sarà), altrimenti la mia foto
                imageSource = card?.imgPath?.takeIf { it.isNotBlank() } ?: myPhotos[key],
                wanted = key in wanted
            )
        }.sortedByDescending { it.wanted }
    }
}