package com.mrfilefixit.feature.exam.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mrfilefixit.core.domain.exam.CertificateSpec
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.exam.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ExamRoute

/**
 * [onPrepareInImageTools] opens Image tools with the exam's limits for one document; [onCompressPdf]
 * opens PDF Compress with a KB target. Both are wired by the app, since features do not see each other.
 */
fun NavGraphBuilder.examScreen(
    onPrepareInImageTools: (examId: String, kind: DocumentKind) -> Unit,
    onCompressPdf: (minKb: Int, maxKb: Int) -> Unit,
) {
    composable<ExamRoute> { ExamListDetail(onPrepareInImageTools, onCompressPdf) }
}

/**
 * Exam picker and exam flow as list-detail: one pane on phones, side by side on tablets, foldables and
 * wide landscape windows. The scaffold handles back navigation between panes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExamListDetail(
    onPrepareInImageTools: (examId: String, kind: DocumentKind) -> Unit,
    onCompressPdf: (minKb: Int, maxKb: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val pickerViewModel: ExamPickerViewModel = hiltViewModel()
    val pickerState by pickerViewModel.state.collectAsStateWithLifecycle()
    val selectedId = navigator.currentDestination?.contentKey
    // One pane (phones, split screen): the detail replaces the list, so it needs a visible back button.
    val singlePane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Hidden
    val back: () -> Unit = { scope.launch { navigator.navigateBack() } }
    val catalogActions = remember(pickerViewModel) {
        ExamCatalogActions(
            onSelect = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
            onQuery = pickerViewModel::onQueryChanged,
            onCategory = pickerViewModel::onCategorySelected,
            onClearFilters = pickerViewModel::onClearFilters,
            onRetry = pickerViewModel::load,
        )
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ExamCatalogScreen(state = pickerState, selectedId = selectedId, actions = catalogActions)
            }
        },
        detailPane = {
            AnimatedPane {
                if (selectedId == null) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            stringResource(R.string.exam_picker_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Dimens.SpaceL),
                        )
                    }
                } else {
                    ExamFlowRoute(
                        profileId = selectedId,
                        onBack = back.takeIf { singlePane },
                        onPrepareInImageTools = { kind -> onPrepareInImageTools(selectedId, kind) },
                        onCompressPdf = onCompressPdf,
                    )
                }
            }
        },
    )
}

@Composable
private fun ExamFlowRoute(
    profileId: String,
    onBack: (() -> Unit)?,
    onPrepareInImageTools: (DocumentKind) -> Unit,
    onCompressPdf: (minKb: Int, maxKb: Int) -> Unit,
) {
    val viewModel = hiltViewModel<ExamFlowViewModel, ExamFlowViewModel.Factory>(key = profileId) { factory ->
        factory.create(profileId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooserTitle = stringResource(CoreR.string.share_chooser_title)
    val errorText = stringResource(CoreR.string.error_unexpected)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExamEffect.Share -> context.shareFile(effect.uri, effect.mimeType, chooserTitle)
                is ExamEffect.ShowError -> snackbar.showSnackbar(errorText)
            }
        }
    }

    val currentPrepare by rememberUpdatedState(onPrepareInImageTools)
    val currentCompress by rememberUpdatedState(onCompressPdf)
    val actions = remember(viewModel) {
        object : ExamFlowActions {
            override fun onPick(key: SlotKey, uri: String) = viewModel.onFilePicked(key, uri)
            override fun onCrop(key: SlotKey, rect: NormalizedRect) = viewModel.onCropChanged(key, rect)
            override fun onRotate(key: SlotKey) = viewModel.onRotate(key)
            override fun onFix(key: SlotKey) = viewModel.onFix(key)
            override fun onCancel(key: SlotKey) = viewModel.onCancel(key)
            override fun onReset(key: SlotKey) = viewModel.onReset(key)
            override fun onSave(key: SlotKey, destination: String) = viewModel.onSave(key, destination)
            override fun onShare(key: SlotKey) = viewModel.onShare(key)
            override fun onMakeComposite() = viewModel.onMakeComposite()
            override fun onRetryLoad() = viewModel.load()
            override fun onPrepareInImageTools(kind: DocumentKind) = currentPrepare(kind)
            override fun onCompressPdf(certificate: CertificateSpec) =
                currentCompress(certificate.minKb, certificate.maxKb)
        }
    }

    ToolScaffold(
        title = state.profile?.shortName ?: stringResource(R.string.exam_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ExamFlowScreen(state, actions, Modifier.padding(padding))
    }
}
