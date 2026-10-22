package com.mrfilefixit.feature.pdf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.share.shareFiles
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.pdf.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object PdfRoute

fun NavGraphBuilder.pdfScreen() {
    composable<PdfRoute> { PdfListDetail() }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PdfListDetail(modifier: Modifier = Modifier) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val selected = navigator.currentDestination?.contentKey?.let { key ->
        PdfTool.entries.firstOrNull { it.name == key }
    }
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                LazyColumn {
                    item {
                        Text(
                            stringResource(R.string.pdf_pick_tool),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(Dimens.SpaceM).semantics { heading() },
                        )
                    }
                    items(PdfTool.entries, key = { it.name }) { tool ->
                        ListItem(
                            headlineContent = { Text(toolLabel(tool)) },
                            colors = if (tool == selected) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                            modifier = Modifier
                                .heightIn(min = Dimens.MinTouchTarget)
                                .selectable(tool == selected, role = Role.Button) {
                                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, tool.name) }
                                },
                        )
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) {
                    Box(Modifier.fillMaxSize()) {
                        Text(stringResource(R.string.pdf_pick_tool), Modifier.align(Alignment.Center))
                    }
                } else {
                    PdfToolRoute(selected)
                }
            }
        },
    )
}

@Composable
private fun PdfToolRoute(tool: PdfTool) {
    val viewModel = hiltViewModel<PdfToolViewModel, PdfToolViewModel.Factory>(key = tool.name) { it.create(tool) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val errorText = stringResource(CoreR.string.error_unexpected)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PdfEffect.Share -> context.shareFiles(effect.uris, effect.mimeType, chooser)
                is PdfEffect.ShowError -> snackbar.showSnackbar(errorText)
            }
        }
    }
    val actions = remember(viewModel) {
        PdfToolActions(
            onPick = viewModel::onFilesPicked,
            onRemove = viewModel::onRemoveFile,
            onMove = viewModel::onMoveFile,
            onOptions = viewModel::onOptionsChanged,
            onRun = viewModel::onRun,
            onCancel = viewModel::onCancel,
            onRevealed = viewModel::onResultRevealed,
            onSave = { index, uri ->
                viewModel.state.value.outputs.getOrNull(index)?.let { viewModel.onSave(it, uri) }
            },
            onShareAll = viewModel::onShareAll,
            onReset = viewModel::onReset,
        )
    }
    Box(Modifier.fillMaxSize()) {
        PdfToolScreen(state, actions)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
