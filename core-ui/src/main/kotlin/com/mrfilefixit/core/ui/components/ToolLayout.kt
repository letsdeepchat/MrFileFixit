package com.mrfilefixit.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.preview.FontScalePreviews
import com.mrfilefixit.core.ui.preview.ThemePreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme

/**
 * The app's one top bar. Tool screens pass [onBack] so users are never stranded in a sub-view; the
 * icon is auto-mirrored for RTL and the button keeps the 48 dp target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("top_bar_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        modifier = modifier,
    )
}

/**
 * Scaffold for every tool screen: [ToolTopAppBar] on top, an optional sticky [bottomBar] for the
 * primary action, and IME padding so the keyboard never covers fields or the action button.
 */
@Composable
fun ToolScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = { ToolTopAppBar(title, onBack = onBack, actions = actions) },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/**
 * Initial state of a tool: the content is centred vertically and horizontally, and still scrolls when
 * it does not fit (landscape phones, 200% font).
 */
@Composable
fun CenteredEmptyState(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Dashed tap-to-upload card used as the primary call to action before any file is picked. */
@Composable
fun UploadDropZone(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.UploadFile,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(Dimens.CardCorner)
    Column(
        modifier = modifier
            .widthIn(max = Dimens.CardMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.dp.toPx(), GAP.dp.toPx()))
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = STROKE.dp.toPx(), pathEffect = dash),
                    cornerRadius = CornerRadius(Dimens.CardCorner.toPx()),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXl)
            .testTag("upload_drop_zone"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        IconBadge(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Round tinted container for a hero or empty-state icon. Decorative: the text next to it says what it is. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Dimens.IconBadge)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Full-width primary action: 52 dp tall, 12 dp corners. Grows with the font instead of clipping. */
@Composable
fun PrimaryActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.ButtonCorner),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.PrimaryButtonHeight),
    ) { Text(text, textAlign = TextAlign.Center) }
}

/** Bar pinned above the navigation bar that holds a screen's primary action(s). */
@Composable
fun StickyActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), tonalElevation = BAR_ELEVATION.dp, shadowElevation = BAR_ELEVATION.dp) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = Dimens.CardMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                content = content,
            )
        }
    }
}

/** Outlined #1E1E1E card that groups one block of options or results. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(Dimens.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            content = content,
        )
    }
}

/** Title for a group of options or results. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

private const val DASH = 8
private const val GAP = 6
private const val STROKE = 1.5f
private const val BAR_ELEVATION = 3

@ThemePreviews
@FontScalePreviews
@Composable
private fun ToolLayoutPreview() {
    MffTheme(dynamicColor = false) {
        ToolScaffold(
            title = "Merge PDFs",
            onBack = {},
            bottomBar = { StickyActionBar { PrimaryActionButton("Merge 3 PDFs", onClick = {}) } },
        ) { padding ->
            CenteredEmptyState(Modifier.padding(padding)) {
                UploadDropZone("Tap to select PDF documents", "Select two or more files", onClick = {})
                SectionHeader("Options")
            }
        }
    }
}
