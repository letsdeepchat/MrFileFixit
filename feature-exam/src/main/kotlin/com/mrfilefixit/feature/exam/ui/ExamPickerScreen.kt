package com.mrfilefixit.feature.exam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamCategory
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.IntBounds
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.ExamDisclaimerCard
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.exam.R
import com.mrfilefixit.core.ui.R as CoreR

@Composable
internal fun ExamPickerScreen(
    state: ExamPickerUiState,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (state) {
                ExamPickerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ExamPickerUiState.Error -> ErrorCard(state.error, onRetry, Modifier.padding(Dimens.SpaceM))
                is ExamPickerUiState.Loaded -> ExamList(state.profiles, selectedId, onSelect)
            }
        }
        LocalAdSlots.current.Banner(BannerPlacement.EXAM_PICKER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExamList(profiles: List<ExamProfile>, selectedId: String?, onSelect: (String) -> Unit) {
    val grouped = profiles.groupBy { it.category }.toSortedMap()
    LazyColumn(
        contentPadding = PaddingValues(vertical = Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
    ) {
        item {
            Column(Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS)) {
                ExamDisclaimerCard()
                Text(
                    text = stringResource(R.string.exam_picker_heading),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(top = Dimens.SpaceM)
                        .semantics { heading() },
                )
            }
        }
        grouped.forEach { (category, items) ->
            item(key = "header-$category") {
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS)
                        .semantics { heading() },
                )
            }
            items(items, key = { it.id }) { profile ->
                val selected = profile.id == selectedId
                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(profile.conductingBody) },
                    trailingContent = { VerificationChip(profile) },
                    colors = if (selected) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .heightIn(min = Dimens.MinTouchTarget)
                        .selectable(selected = selected, role = Role.Button, onClick = { onSelect(profile.id) }),
                )
            }
        }
    }
}

@Composable
internal fun VerificationChip(profile: ExamProfile) {
    val label = when (profile.verification) {
        VerificationStatus.VERIFIED -> stringResource(CoreR.string.verification_verified, profile.lastVerified.orEmpty())
        VerificationStatus.UNVERIFIED -> stringResource(CoreR.string.verification_unverified)
    }
    SuggestionChip(onClick = {}, enabled = false, label = { Text(label) })
}

@Composable
internal fun categoryLabel(category: ExamCategory): String = stringResource(
    when (category) {
        ExamCategory.SSC -> R.string.exam_category_SSC
        ExamCategory.UPSC -> R.string.exam_category_UPSC
        ExamCategory.IBPS -> R.string.exam_category_IBPS
        ExamCategory.RRB -> R.string.exam_category_RRB
        ExamCategory.NEET -> R.string.exam_category_NEET
        ExamCategory.STATE_PSC -> R.string.exam_category_STATE_PSC
        ExamCategory.OTHER -> R.string.exam_category_OTHER
    },
)

/** Sample data for previews and UI tests. */
internal val PreviewProfiles = listOf(
    ExamProfile(
        id = "ibps-po",
        name = "IBPS Probationary Officer (PO)",
        category = ExamCategory.IBPS,
        conductingBody = "Institute of Banking Personnel Selection",
        sourceUrl = "https://www.ibps.in/",
        lastVerified = null,
        verification = VerificationStatus.UNVERIFIED,
        documents = listOf(
            DocumentSpec(DocumentKind.PHOTO, setOf(ImageFormat.JPEG), 20, 50, IntBounds.exactly(200), IntBounds.exactly(230), 200, 230),
            DocumentSpec(DocumentKind.SIGNATURE, setOf(ImageFormat.JPEG), 10, 20, IntBounds.exactly(140), IntBounds.exactly(60), 140, 60),
        ),
    ),
    ExamProfile(
        id = "upsc-cse",
        name = "UPSC Civil Services Examination",
        category = ExamCategory.UPSC,
        conductingBody = "Union Public Service Commission",
        sourceUrl = "https://upsconline.gov.in/",
        lastVerified = null,
        verification = VerificationStatus.UNVERIFIED,
        documents = listOf(
            DocumentSpec(DocumentKind.PHOTO, setOf(ImageFormat.JPEG), 20, 300, IntBounds(350, 1000), IntBounds(350, 1000), 450, 550),
        ),
    ),
)

@AllPreviews
@Composable
private fun ExamPickerPreview() {
    MffTheme(dynamicColor = false) {
        ExamPickerScreen(ExamPickerUiState.Loaded(PreviewProfiles), selectedId = "ibps-po", onSelect = {}, onRetry = {})
    }
}
