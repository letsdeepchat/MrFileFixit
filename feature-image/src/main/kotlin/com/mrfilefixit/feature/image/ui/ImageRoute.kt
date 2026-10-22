package com.mrfilefixit.feature.image.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.share.shareFiles
import kotlinx.serialization.Serializable

@Serializable
data object ImageRoute

fun NavGraphBuilder.imageScreen() {
    composable<ImageRoute> { ImageToolRoute() }
}

@Composable
private fun ImageToolRoute(viewModel: ImageToolViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingBatches.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val errorText = stringResource(CoreR.string.error_unexpected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImageEffect.Share -> context.shareFiles(effect.uris, effect.mimeType, chooser)
                is ImageEffect.ShowError -> snackbar.showSnackbar(errorText)
            }
        }
    }
    val actions = remember(viewModel) {
        ImageToolActions(
            onTool = viewModel::onToolSelected,
            onPick = viewModel::onFilesPicked,
            onOptions = viewModel::onOptionsChanged,
            onRun = viewModel::onRun,
            onCancel = viewModel::onCancel,
            onRevealed = viewModel::onResultRevealed,
            onRewardEarned = viewModel::onRewardEarned,
            onSave = { index, uri ->
                viewModel.state.value.outcome?.images?.getOrNull(index)?.let { viewModel.onSave(it.file, uri) }
            },
            onShareAll = viewModel::onShareAll,
            onReset = viewModel::onReset,
            canEncode = viewModel::canEncode,
        )
    }
    Box(Modifier.fillMaxSize()) {
        ImageToolScreen(state, remaining, actions)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
