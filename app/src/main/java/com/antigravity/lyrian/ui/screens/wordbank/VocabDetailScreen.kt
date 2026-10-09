package com.antigravity.lyrian.ui.screens.wordbank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.local.VocabEntity

/**
 * 單字詳情頁。
 *
 * 顯示該字的完整字典資訊：單字、詞性縮寫、音標（英式或美式）、字義、
 * 以及它在歌詞中出現的語境。
 *
 * 字典資料在進入時補查（若尚未查過），之後不再連外。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDetailScreen(
    onBack: () -> Unit,
    viewModel: VocabDetailViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wordbank_detail_title)) },
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
    ) { innerPadding ->
        val vocab = entry
        if (vocab == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeadlineCard(vocab)

            vocab.translation?.let { meaning ->
                InfoSection(
                    title = stringResource(R.string.wordbank_detail_meaning),
                    body = meaning,
                )
            }

            if (vocab.phonetic == null && vocab.translation == null) {
                Text(
                    text = stringResource(R.string.wordbank_detail_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            vocab.contextLine?.let { context ->
                InfoSection(
                    title = stringResource(R.string.wordbank_detail_context),
                    body = context,
                )
            }
        }
    }
}

/** 頂部大字卡：單字 + 詞性 + 音標。 */
@Composable
private fun HeadlineCard(vocab: VocabEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = vocab.surfaceForm ?: vocab.lemma,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                vocab.partOfSpeech?.let { pos ->
                    Text(
                        text = pos,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            val pronunciation = buildString {
                vocab.phonetic?.let { append(it) }
                vocab.phoneticAccent?.let {
                    if (isNotEmpty()) append("  ")
                    append("(").append(it).append(")")
                }
            }

            if (pronunciation.isNotBlank()) {
                Text(
                    text = pronunciation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
