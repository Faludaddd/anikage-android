package com.anikage.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.browse.BrowseViewModel
import com.anikage.app.ui.components.AnimeCard

/** Music Explorer mirrors the website search surface using the catalogue as the
 * source of anime titles. Selecting a result opens the anime's music-capable
 * details page instead of leaving a dead placeholder screen. */
@Composable
fun MusicScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val browseViewModel: BrowseViewModel = viewModel(factory = BrowseViewModel.factory(repo))
    val state by browseViewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.length >= 2) {
            kotlinx.coroutines.delay(350)
            browseViewModel.applyFilters(com.anikage.app.ui.browse.BrowseFilters(query = query))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Music Explorer", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Text(
            "Search anime openings, endings, and soundtracks",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Type anime title (e.g. 'Chainsaw Man')…") },
            label = { Text("Search music") },
        )
        when {
            state.loading && query.length >= 2 -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            query.length < 2 -> Text(
                "Search thousands of openings, endings, and soundtracks.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp),
            )
            state.items.isEmpty() -> Text(
                "No anime found. Try another title.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.id }) { anime ->
                    AnimeCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }
}
