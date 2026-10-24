package com.mrfilefixit.app.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mrfilefixit.app.R
import com.mrfilefixit.app.navigation.TopLevelDestination
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.IconBadge
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme

private val tools = listOf(
    TopLevelDestination.EXAM to R.string.home_exam_body,
    TopLevelDestination.PDF to R.string.home_pdf_body,
    TopLevelDestination.IMAGE to R.string.home_image_body,
    TopLevelDestination.SCAN to R.string.home_scan_body,
)

@Composable
fun HomeScreen(onOpen: (TopLevelDestination) -> Unit, onOpenSettings: () -> Unit) {
    ToolScaffold(
        title = stringResource(R.string.app_name),
        modifier = Modifier.testTag("home"),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
            }
        },
        bottomBar = { LocalAdSlots.current.Banner(BannerPlacement.HOME, Modifier.fillMaxWidth()) },
    ) { padding ->
        // Adaptive grid: one column on phones, two or more on tablets and in landscape.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 280.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.home_offline_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tools, key = { it.first.name }) { (destination, body) ->
                ToolCard(destination, body, onClick = { onOpen(destination) })
            }
        }
    }
}

@Composable
private fun ToolCard(destination: TopLevelDestination, body: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouchTarget),
    ) {
        Row(
            Modifier.padding(Dimens.SectionPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(destination.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                Text(stringResource(destination.label), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@AllPreviews
@Composable
private fun HomePreview() {
    MffTheme(dynamicColor = false) { HomeScreen(onOpen = {}, onOpenSettings = {}) }
}
