package com.mrfilefixit.feature.scan.ui

import android.os.Build
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
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.share.shareText
import com.mrfilefixit.feature.scan.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ScanRoute

fun NavGraphBuilder.scanScreen() {
    composable<ScanRoute> { ScanRouteContent() }
}

@Composable
private fun ScanRouteContent(viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val copied = stringResource(R.string.scan_copied)
    val errorText = stringResource(CoreR.string.error_unexpected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ScanEffect.Share -> context.shareFile(effect.uri, effect.mimeType, chooser)
                is ScanEffect.ShowError -> snackbar.showSnackbar(errorText)
            }
        }
    }
    val actions = remember(viewModel) {
        ScanActions(
            onCaptured = viewModel::onScanCaptured,
            onScannerUnavailable = viewModel::onScannerUnavailable,
            onPickOcr = viewModel::onOcrFilesPicked,
            onScript = viewModel::onScriptChanged,
            onFilter = viewModel::onFilterSelected,
            onRecognize = viewModel::onRecognize,
            onCancel = viewModel::onCancel,
            onSave = viewModel::onSave,
            onShare = viewModel::onShare,
            onCopyText = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(sensitiveClip(text)))
                    // Android 13+ shows its own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.showSnackbar(copied)
                }
            },
            onShareText = { text -> context.shareText(text, chooser) },
            onReset = viewModel::onReset,
        )
    }
    ScanScreen(state, actions, snackbarHost = { SnackbarHost(snackbar) })
}
