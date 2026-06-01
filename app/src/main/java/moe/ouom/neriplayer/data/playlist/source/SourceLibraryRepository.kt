package moe.ouom.neriplayer.data.playlist.source

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class SourceLibraryItem(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val source: String,
    val browseId: String? = null,
    val playlistId: String? = null,
    val subtitle: String? = null,
    val fid: Long? = null,
    val mid: Long? = null,
    val bvid: String? = null,
    val addedTime: Long = System.currentTimeMillis(),
    val sortOrder: Long = addedTime
)

class SourceLibraryRepository private constructor(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "source_library_playlists.json")

    private val _items = MutableStateFlow<List<SourceLibraryItem>>(emptyList())
    val items: StateFlow<List<SourceLibraryItem>> = _items

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val list = try {
            if (!file.exists()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<SourceLibraryItem>>() {}.type
                gson.fromJson<List<SourceLibraryItem>>(file.readText(), type).orEmpty()
            }
        } catch (_: Exception) {
            emptyList()
        }
        publish(list)
    }

    private fun publish(items: List<SourceLibraryItem>) {
        _items.value = items
            .groupBy { "${it.source}:${it.id}" }
            .map { (_, snapshots) ->
                snapshots.maxByOrNull { it.sortOrder.takeIf { order -> order > 0L } ?: it.addedTime }!!
            }
            .sortedWith(compareByDescending<SourceLibraryItem> { it.sortOrder }.thenByDescending { it.addedTime })
        saveToDisk()
    }

    private fun saveToDisk() {
        runCatching {
            val parent = file.parentFile ?: context.filesDir
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(gson.toJson(_items.value))
            if (!tmp.renameTo(file)) {
                file.writeText(gson.toJson(_items.value))
                tmp.delete()
            }
        }
    }

    suspend fun replaceSourceItems(
        source: String,
        selectedItems: List<SourceLibraryItem>
    ) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existingByKey = _items.value.associateBy { "${it.source}:${it.id}" }
            val normalizedSelected = selectedItems
                .distinctBy { "${it.source}:${it.id}" }
                .mapIndexed { index, item ->
                    val existing = existingByKey["${item.source}:${item.id}"]
                    item.copy(
                        source = source,
                        addedTime = existing?.addedTime ?: now,
                        sortOrder = now + (selectedItems.size - index).toLong()
                    )
                }
            publish(_items.value.filterNot { it.source == source } + normalizedSelected)
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: SourceLibraryRepository? = null

        fun getInstance(context: Context): SourceLibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SourceLibraryRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
