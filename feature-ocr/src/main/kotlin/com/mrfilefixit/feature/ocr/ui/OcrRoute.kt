package com.mrfilefixit.feature.ocr.ui

import android.content.ClipData
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.share.shareText
import com.mrfilefixit.feature.ocr.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Text recognition, opened from PDF tools, Image tools or Scan. [uris] pre-loads files: Scan passes the
 * pages it just captured (or the files picked there) so they do not have to be picked again.
 */
@Serializable
data class OcrRoute(val uris: List<String> = emptyList())

fun NavGraphBuilder.ocrScreen(onBack: () -> Unit) {
    composable<OcrRoute> { entry -> OcrRouteContent(entry.toRoute<OcrRoute>(), onBack) }
}

@Composable
private fun OcrRouteContent(route: OcrRoute, onBack: () -> Unit) {
    val viewModel = hiltViewModel<OcrViewModel, OcrViewModel.Factory> { it.create(route.uris) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val copied = stringResource(R.string.ocr_copied)
    val errorText = stringResource(CoreR.string.error_unexpected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OcrEffect.Share -> context.shareFile(effect.uri, effect.mimeType, chooser)
                is OcrEffect.ShowError -> snackbar.showSnackbar(errorText)
            }
        }
    }
    val actions = remember(viewModel) {
        OcrActions(
            onBack = onBack,
            onPick = viewModel::onFilesPicked,
            onScript = viewModel::onScriptChanged,
            onRecognize = viewModel::onRecognize,
            onCancel = viewModel::onCancel,
            onCopy = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(sensitiveClip(text)))
                    // Android 13+ shows its own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(copied)
                }
            },
            onShareText = { text -> context.shareText(text, chooser) },
            onExport = viewModel::onExport,
            onSave = viewModel::onSave,
            onShare = viewModel::onShare,
            onReset = viewModel::onReset,
        )
    }
    OcrScreen(state, actions, snackbarHost = { SnackbarHost(snackbar) })
}

/** Clip data for OCR text. Marked sensitive so Android 13+ hides it from the clipboard preview. */
private fun sensitiveClip(text: String): ClipData = ClipData.newPlainText("text", text).apply {
    description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
}

// ClipDescription.EXTRA_IS_SENSITIVE is API 33; the literal key is honoured there and ignored below.
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
