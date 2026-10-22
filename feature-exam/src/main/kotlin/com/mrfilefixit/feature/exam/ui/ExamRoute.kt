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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mrfilefixit.core.domain.image.NormalizedRect
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.exam.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ExamRoute

fun NavGraphBuilder.examScreen() {
    composable<ExamRoute> { ExamListDetail() }
}

/**
 * Exam picker and exam flow as list-detail: one pane on phones, side by side on tablets, foldables and
 * wide landscape windows. The scaffold handles back navigation between panes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExamListDetail(modifier: Modifier = Modifier) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val pickerViewModel: ExamPickerViewModel = hiltViewModel()
    val pickerState by pickerViewModel.state.collectAsStateWithLifecycle()
    val selectedId = navigator.currentDestination?.contentKey

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ExamPickerScreen(
                    state = pickerState,
                    selectedId = selectedId,
                    onSelect = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
                    onRetry = pickerViewModel::load,
                )
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
                    ExamFlowRoute(profileId = selectedId)
                }
            }
        },
    )
}

@Composable
private fun ExamFlowRoute(profileId: String) {
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

    val actions = remember(viewModel) {
        object : ExamFlowActions {
            override fun onPick(key: SlotKey, uri: String) = viewModel.onFilePicked(key, uri)
            override fun onCrop(key: SlotKey, rect: NormalizedRect) = viewModel.onCropChanged(key, rect)
            override fun onRotate(key: SlotKey) = viewModel.onRotate(key)
            override fun onFix(key: SlotKey) = viewModel.onFix(key)
            override fun onCancel(key: SlotKey) = viewModel.onCancel(key)
            override fun onReset(key: SlotKey) = viewModel.onReset(key)
            override fun onRevealed(key: SlotKey) = viewModel.onResultRevealed(key)
            override fun onSave(key: SlotKey, destination: String) = viewModel.onSave(key, destination)
            override fun onShare(key: SlotKey) = viewModel.onShare(key)
            override fun onMakeComposite() = viewModel.onMakeComposite()
            override fun onRetryLoad() = viewModel.load()
        }
    }

    Box(Modifier.fillMaxSize()) {
        ExamFlowScreen(state, actions)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
