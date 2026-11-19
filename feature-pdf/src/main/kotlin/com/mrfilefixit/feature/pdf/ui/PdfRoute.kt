package com.mrfilefixit.feature.pdf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.navigation.ReportInnerScreen
import com.mrfilefixit.core.ui.share.shareFiles
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.pdf.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * PDF tools. [tool] opens one tool directly (by [PdfTool] name) and [minKb]/[maxKb] give it a size
 * target, e.g. when the exam screen sends a certificate to Compress.
 */
@Serializable
data class PdfRoute(val tool: String? = null, val minKb: Int? = null, val maxKb: Int? = null)

/** PDF tools opened on their own screen (from an exam), with [onExit] as the tool list's back button. */
fun NavGraphBuilder.pdfScreen(onOpenOcr: () -> Unit = {}, onExit: () -> Unit = {}) {
    composable<PdfRoute> { entry -> PdfListDetail(entry.toRoute<PdfRoute>(), onOpenOcr, onExit = onExit) }
}

/**
 * PDF tools as list-detail. In the PDF tab [onExit] is null: the tool list is the tab's root. A tool open on
 * its own (phones) is an inner screen with a back button.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PdfListDetail(
    route: PdfRoute,
    onOpenOcr: () -> Unit,
    modifier: Modifier = Modifier,
    onExit: (() -> Unit)? = null,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val selected = navigator.currentDestination?.contentKey?.let { key ->
        PdfTool.entries.firstOrNull { it.name == key }
    }
    val singlePane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    ReportInnerScreen(inner = singlePane && selected != null)
    val sizeTarget = route.maxKb?.let { KbTarget(route.minKb ?: 0, it) }
    // Open the requested tool once; after that the user's own navigation wins (also across rotation).
    var openedRequested by rememberSaveable(route) { mutableStateOf(false) }
    LaunchedEffect(route) {
        val requested = PdfTool.entries.firstOrNull { it.name == route.tool }
        if (requested != null && !openedRequested) {
            openedRequested = true
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, requested.name)
        }
    }
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                PdfToolPicker(
                    selected = selected,
                    onOpenOcr = onOpenOcr,
                    onBack = onExit,
                    onSelect = { tool ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, tool.name) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            stringResource(R.string.pdf_pick_tool_detail),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Dimens.SpaceL),
                        )
                    }
                } else {
                    val back: () -> Unit = { scope.launch { navigator.navigateBack() } }
                    PdfToolRoute(
                        tool = selected,
                        // The size target only applies to the tool it was sent to.
                        sizeTarget = sizeTarget.takeIf { selected.name == route.tool },
                        onBack = back.takeIf { singlePane },
                    )
                }
            }
        },
    )
}

@Composable
private fun PdfToolRoute(tool: PdfTool, sizeTarget: KbTarget?, onBack: (() -> Unit)?) {
    val viewModel = hiltViewModel<PdfToolViewModel, PdfToolViewModel.Factory>(key = "${tool.name}-$sizeTarget") {
        it.create(tool, sizeTarget)
    }
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
            onSave = { index, uri ->
                viewModel.state.value.outputs.getOrNull(index)?.let { viewModel.onSave(it, uri) }
            },
            onShareAll = viewModel::onShareAll,
            onReset = viewModel::onReset,
        )
    }
    PdfToolScreen(state, actions, onBack = onBack, snackbarHost = { SnackbarHost(snackbar) })
}
