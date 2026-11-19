package com.mrfilefixit.feature.image.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.SectionHeader
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.thumbnail.Thumbnail
import com.mrfilefixit.feature.image.R
import kotlin.math.roundToInt

/**
 * The Remove background tab: what goes behind the person (white, exam blue, see-through, any colour or a photo of
 * the user's choice), how soft the outline is, then the output format. The live before / after sits above.
 */
@Composable
internal fun BackgroundOptionsCards(state: ImageToolUiState, actions: ImageToolActions, modifier: Modifier = Modifier) {
    val background = state.options.background
    val update = { change: (BackgroundOptions) -> BackgroundOptions ->
        actions.onOptions { it.copy(background = change(it.background)) }
    }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            keepReadAccess(context, uri)
            update { it.copy(choice = BackgroundChoice.PICTURE, pictureUri = uri.toString()) }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // The edge slider moves freely; the preview is only redone when it is released.
    var edge by remember(background.edge) { mutableFloatStateOf(background.edge) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
        SectionCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.image_bg_title))
            Text(
                stringResource(R.string.image_bg_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(BackgroundChoice.entries, background.choice, { choiceLabel(it) }) { choice ->
                if (choice == BackgroundChoice.PICTURE && background.pictureUri == null) {
                    pick()
                } else {
                    update { it.copy(choice = choice) }
                }
            }
            when (background.choice) {
                BackgroundChoice.COLOR -> ColorPicker(
                    background.color,
                    { c -> update { it.copy(color = c) } },
                    title = stringResource(R.string.image_bg_color_title),
                )
                BackgroundChoice.PICTURE -> PictureRow(background.pictureUri, pick)
                BackgroundChoice.TRANSPARENT -> Text(
                    stringResource(R.string.image_bg_png_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
            Text(stringResource(R.string.image_bg_edge, (edge * PERCENT).roundToInt()))
            Slider(
                value = edge,
                onValueChange = { edge = it },
                onValueChangeFinished = { update { it.copy(edge = edge) } },
                valueRange = 0f..1f,
                modifier = Modifier.testTag("bg_edge"),
            )
        }
        if (background.choice != BackgroundChoice.TRANSPARENT || state.options.format == ImageFormat.WEBP) {
            FormatCard(state, actions)
        }
    }
}

@Composable
private fun PictureRow(pictureUri: String?, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        if (pictureUri != null) {
            Thumbnail(pictureUri, stringResource(CoreR.string.cd_thumbnail), Dimens.ThumbnailSize)
        }
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier
                .heightIn(min = Dimens.MinTouchTarget)
                .testTag("bg_pick"),
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Text(
                stringResource(if (pictureUri == null) R.string.image_bg_pick else R.string.image_bg_change),
                Modifier.padding(start = Dimens.SpaceS),
            )
        }
    }
}

/**
 * Keeps the picked background readable after the picker closes, so a batch that runs in the background can
 * still open it. Not every source grants this; the picture still works while the app is open.
 */
private fun keepReadAccess(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // A one-time grant: fine for the preview and for runs while the app is open.
    }
}

@Composable
private fun choiceLabel(choice: BackgroundChoice): String = stringResource(
    when (choice) {
        BackgroundChoice.WHITE -> R.string.image_bg_WHITE
        BackgroundChoice.EXAM_BLUE -> R.string.image_bg_EXAM_BLUE
        BackgroundChoice.TRANSPARENT -> R.string.image_bg_TRANSPARENT
        BackgroundChoice.COLOR -> R.string.image_bg_COLOR
        BackgroundChoice.PICTURE -> R.string.image_bg_PICTURE
    },
)

private const val PERCENT = 100
