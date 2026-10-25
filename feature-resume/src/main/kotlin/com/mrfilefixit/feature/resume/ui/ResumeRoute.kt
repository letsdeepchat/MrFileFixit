package com.mrfilefixit.feature.resume.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.error.appErrorMessage
import com.mrfilefixit.core.ui.share.shareFiles
import kotlinx.serialization.Serializable

/** Resume builder tab: saved drafts, template gallery and editor. */
@Serializable
data object ResumeRoute

fun NavGraphBuilder.resumeScreen() {
    composable<ResumeRoute> { ResumeTabRoute() }
}

@Composable
private fun ResumeTabRoute(viewModel: ResumeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    var snackError by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ResumeEffect.Share -> context.shareFiles(listOf(effect.uri), effect.mimeType, chooser)
                is ResumeEffect.ShowError -> snackError = effect.error
            }
        }
    }
    snackError?.let { error ->
        val message = appErrorMessage(error)
        LaunchedEffect(error) {
            snackbar.showSnackbar(message)
            snackError = null
        }
    }
    // Back walks up inside the tab (crop → editor → list) before leaving it.
    BackHandler(enabled = state.page != ResumePage.LIST || state.photoCrop != null) { viewModel.onBack() }
    val snackbarHost = @Composable { SnackbarHost(snackbar) }
    when (state.page) {
        ResumePage.LIST -> ResumeListScreen(
            state = state,
            onCreate = { viewModel.onOpenTemplates(restyle = false) },
            onOpen = viewModel::onOpenDraft,
            onDelete = viewModel::onDeleteDraft,
            onImport = viewModel::onImport,
            onDismissError = viewModel::onDismissError,
            snackbarHost = snackbarHost,
        )
        ResumePage.TEMPLATES -> TemplateGalleryScreen(
            current = if (state.restyling) state.resume?.templateId else null,
            onChoose = viewModel::onTemplateChosen,
            onBack = { viewModel.onBack() },
        )
        ResumePage.EDITOR -> ResumeEditorScreen(
            state = state,
            actions = remember(viewModel) {
                ResumeEditorActions(
                    onBack = { viewModel.onBack() },
                    onEdit = viewModel::edit,
                    onChangeTemplate = { viewModel.onOpenTemplates(restyle = true) },
                    onPhoto = viewModel::onPhoto,
                    onExport = viewModel::onExport,
                    onCancelExport = viewModel::onCancelExport,
                    onSave = viewModel::onSave,
                    onShare = viewModel::onShare,
                    newId = viewModel.newId,
                )
            },
            snackbarHost = snackbarHost,
        )
    }
}
