package com.araswqm.hachi.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.db.dao.CacheStatRow
import com.araswqm.hachi.data.db.dao.MessageDao
import com.araswqm.hachi.data.db.dao.MemoryStateDao
import com.araswqm.hachi.data.db.dao.ProactiveLogDao
import com.araswqm.hachi.data.db.entity.MemoryStateEntity
import com.araswqm.hachi.data.db.entity.ProactiveLogEntity
import com.araswqm.hachi.data.memory.IntimacyEngine
import com.araswqm.hachi.data.repo.CharacterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

/** Yakınlık satırı: karakter adı, seviye, son ham puan ve pencere ortalaması. */
data class IntimacyRow(
    val name: String?,
    val level: String,
    val raw: Int,
    val average: Double,
)

/** Önbellek satırı: karakter adı ve token toplamları. */
data class CacheRow(
    val name: String?,
    val hit: Long,
    val miss: Long,
    val requests: Long,
)

/** Günlük satırı: zaman, aşama, sonuç ve gerekçe. */
data class LogRow(
    val timestamp: Long,
    val stage: String,
    val result: String,
    val reason: String,
)

data class DebugUiState(
    val intimacy: List<IntimacyRow> = emptyList(),
    val cache: List<CacheRow> = emptyList(),
    val logs: List<LogRow> = emptyList(),
) {
    val isEmpty: Boolean get() = intimacy.isEmpty() && cache.isEmpty() && logs.isEmpty()
}

/**
 * Gizli hata ayıklama ekranı.
 *
 * **Yakınlık puanı yalnızca burada görünür** (bkz. proje kuralları). ViewModel
 * ham veriyi üretir; metin biçimlendirmesi (yerelleştirme) Composable'da yapılır,
 * çünkü ViewModel'de `Context` yoktur.
 *
 * `name` alanları **null** olabilir: silinen karakterlerin satırları CASCADE ile
 * temizlense de, boş isimli karakterler ve yarış durumları için arayüz
 * `debug_deleted_character` metnine düşer.
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    characters: CharacterRepository,
    memoryStateDao: MemoryStateDao,
    messageDao: MessageDao,
    proactiveLogDao: ProactiveLogDao,
    private val intimacy: IntimacyEngine,
    private val json: Json,
) : ViewModel() {

    val uiState: StateFlow<DebugUiState> = combine(
        characters.observeAll(),
        memoryStateDao.observeAll(),
        messageDao.observeCacheStats(),
        proactiveLogDao.observeRecentAll(Tuning.DEBUG_LOG_LIMIT),
    ) { chars, states, cacheStats, logs ->
        val names = chars.associate { it.id to it.name.trim().takeIf(String::isNotEmpty) }
        val now = System.currentTimeMillis()
        DebugUiState(
            intimacy = states.map { intimacyRow(names[it.characterId], it, now) },
            cache = cacheStats.map { cacheRow(names[it.characterId], it) },
            logs = logs.map { it.toLogRow() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState())

    private fun intimacyRow(
        name: String?,
        state: MemoryStateEntity,
        now: Long,
    ): IntimacyRow {
        val history = parseRawHistory(state.intimacyRawHistoryJson)
        val window = history.takeLast(Tuning.INTIMACY_MOVING_AVERAGE_WINDOW)
        val effective = intimacy.effectiveScore(state.intimacyScore, state.lastDecayAt, now)
        return IntimacyRow(
            name = name,
            level = intimacy.level(effective),
            raw = window.lastOrNull() ?: 0,
            average = if (window.isEmpty()) 0.0 else window.average(),
        )
    }

    private fun cacheRow(name: String?, stat: CacheStatRow): CacheRow = CacheRow(
        name = name,
        hit = stat.hit,
        miss = stat.miss,
        requests = stat.requests,
    )

    /** Bozuk/eski JSON sessizce boş listeye düşer; ekranı çökertmez. */
    private fun parseRawHistory(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Int>>(raw) }.getOrDefault(emptyList())
    }

    private fun ProactiveLogEntity.toLogRow(): LogRow = LogRow(
        timestamp = timestamp,
        stage = stage,
        result = result,
        reason = reason,
    )
}
