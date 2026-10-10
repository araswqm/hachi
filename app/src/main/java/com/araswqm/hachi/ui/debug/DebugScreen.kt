package com.araswqm.hachi.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.araswqm.hachi.R
import com.araswqm.hachi.ui.common.relativeTime
import java.util.Locale

/**
 * Gizli hata ayıklama ekranı (Ayarlar'da sürüme 5 kez dokununca açılır).
 *
 * **Yakınlık puanı yalnızca burada görünür.** Üç bölüm: yakınlık puanları,
 * önbellek isabeti ve kendiliğinden mesaj günlüğü ("neden yazmadı?").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted = stringResource(R.string.debug_deleted_character)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // --- Yakınlık ---
            item(key = "intimacy_header") { Header(stringResource(R.string.debug_intimacy)) }
            if (state.intimacy.isEmpty()) {
                item(key = "intimacy_empty") { EmptyLine() }
            } else {
                items(state.intimacy, key = { "intimacy_${it.name}_${it.level}_${it.raw}" }) { row ->
                    BodyLine(
                        stringResource(
                            R.string.debug_intimacy_line,
                            row.name ?: deleted,
                            row.level,
                            row.raw,
                            formatAverage(row.average),
                        ),
                    )
                }
            }

            item(key = "cache_header") {
                Header(stringResource(R.string.debug_cache_tokens), topPadding = 16.dp)
            }
            if (state.cache.isEmpty()) {
                item(key = "cache_empty") { EmptyLine() }
            } else {
                items(state.cache, key = { "cache_${it.name}_${it.requests}" }) { row ->
                    BodyLine(
                        stringResource(
                            R.string.debug_cache_line,
                            row.name ?: deleted,
                            row.hit,
                            row.miss,
                            row.requests,
                        ),
                    )
                }
            }

            item(key = "log_header") {
                Header(stringResource(R.string.debug_proactive_log), topPadding = 16.dp)
            }
            if (state.logs.isEmpty()) {
                item(key = "log_empty") { EmptyLine() }
            } else {
                items(state.logs, key = { "log_${it.timestamp}_${it.stage}_${it.result}" }) { row ->
                    val detail = if (row.reason.isBlank()) row.result else "${row.result} (${row.reason})"
                    BodyLine(
                        stringResource(
                            R.string.debug_log_entry,
                            relativeTime(row.timestamp),
                            row.stage,
                            detail,
                        ),
                    )
                }
            }
        }
    }
}

/** Pencere ortalaması; tek ondalık, cihaz yerelinden bağımsız (nokta ayracı). */
private fun formatAverage(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun Header(text: String, topPadding: Dp = 0.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BodyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

@Composable
private fun EmptyLine() {
    Text(
        text = stringResource(R.string.debug_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
