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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.error.appErrorMessage
import com.mrfilefixit.core.ui.navigation.ReportInnerScreen
import com.mrfilefixit.core.ui.share.shareFiles

/**
 * The Resume & Biodata tab: the hub (two create actions and saved drafts), then either the resume builder or
 * the biodata builder.
 */
@Composable
fun ResumeTab(modifier: Modifier = Modifier) {
    val viewModel: ResumeViewModel = hiltViewModel()
    val biodataVm: BiodataViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val biodata by biodataVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val chooser = stringResource(CoreR.string.share_chooser_title)
    var snackError by remember { mutableStateOf<AppError?>(null) }
    val resources = LocalResources.current
    LaunchedEffect(biodataVm, resources) { biodataVm.labels = ResourceBiodataLabels(resources) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ResumeEffect.Share -> context.shareFiles(listOf(effect.uri), effect.mimeType, chooser)
                is ResumeEffect.ShowError -> snackError = effect.error
            }
        }
    }
    LaunchedEffect(biodataVm) {
        biodataVm.effects.collect { effect ->
            when (effect) {
                is BiodataEffect.Share -> context.shareFiles(listOf(effect.uri), effect.mimeType, chooser)
                is BiodataEffect.ShowError -> snackError = effect.error
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
    ReportInnerScreen(state.page != ResumePage.LIST || biodata.page != BiodataPage.NONE)
    // Back walks up inside the tab (crop → editor → list) before leaving it.
    BackHandler(enabled = biodata.page != BiodataPage.NONE || biodata.photoCrop != null) { biodataVm.onBack() }
    BackHandler(
        enabled = biodata.page == BiodataPage.NONE && (state.page != ResumePage.LIST || state.photoCrop != null),
    ) {
        viewModel.onBack()
    }
    val snackbarHost = @Composable { SnackbarHost(snackbar) }
    when {
        biodata.page == BiodataPage.TEMPLATES -> BiodataTemplatesScreen(
            current = if (biodata.restyling) biodata.biodata?.templateId else null,
            onlyKind = if (biodata.restyling) biodata.template?.kind else null,
            onChoose = biodataVm::onTemplateChosen,
            onBack = { biodataVm.onBack() },
            modifier = modifier,
        )
        biodata.page == BiodataPage.EDITOR -> BiodataEditorScreen(
            state = biodata,
            actions = remember(biodataVm) {
                BiodataEditorActions(
                    onBack = { biodataVm.onBack() },
                    onEdit = biodataVm::edit,
                    onChangeDesign = { biodataVm.onOpenTemplates(restyle = true) },
                    onPreview = biodataVm::onPreview,
                    onPhoto = biodataVm::onPhoto,
                    onExport = biodataVm::onExport,
                    onCancelExport = biodataVm::onCancelExport,
                    onSave = biodataVm::onSave,
                    onShare = biodataVm::onShare,
                )
            },
            modifier = modifier,
            snackbarHost = snackbarHost,
        )
        else -> ResumePages(state, viewModel, modifier, snackbarHost, biodata, biodataVm)
    }
}

@Composable
private fun ResumePages(
    state: ResumeUiState,
    viewModel: ResumeViewModel,
    modifier: Modifier,
    snackbarHost: @Composable () -> Unit,
    biodata: BiodataUiState,
    biodataVm: BiodataViewModel,
) {
    when (state.page) {
        ResumePage.LIST -> ResumeListScreen(
            state = state.copy(error = state.error ?: biodata.error),
            biodataDrafts = biodata.drafts,
            actions = remember(viewModel, biodataVm) {
                ResumeHubActions(
                    onCreateResume = { viewModel.onOpenTemplates(restyle = false) },
                    onCreateBiodata = { biodataVm.onOpenTemplates(restyle = false) },
                    onOpenResume = viewModel::onOpenDraft,
                    onOpenBiodata = biodataVm::onOpenDraft,
                    onDeleteResume = viewModel::onDeleteDraft,
                    onDeleteBiodata = biodataVm::onDeleteDraft,
                    onImport = viewModel::onImport,
                    onDismissError = {
                        viewModel.onDismissError()
                        biodataVm.onDismissError()
                    },
                )
            },
            modifier = modifier,
            snackbarHost = snackbarHost,
        )
        ResumePage.TEMPLATES -> TemplateGalleryScreen(
            current = if (state.restyling) state.resume?.templateId else null,
            onChoose = viewModel::onTemplateChosen,
            onBack = { viewModel.onBack() },
            modifier = modifier,
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
            modifier = modifier,
            snackbarHost = snackbarHost,
        )
    }
}
