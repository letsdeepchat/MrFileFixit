package com.mrfilefixit.feature.exam.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.exam.CertificateFormat
import com.mrfilefixit.core.domain.exam.CertificateSpec
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.exam.DocumentSpec
import com.mrfilefixit.core.domain.exam.ExamCategory
import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.IntBounds
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.image.ImageFormat
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.ads.BannerPlacement
import com.mrfilefixit.core.ui.ads.LocalAdSlots
import com.mrfilefixit.core.ui.components.ErrorCard
import com.mrfilefixit.core.ui.components.ExamDisclaimerCard
import com.mrfilefixit.core.ui.components.PillRow
import com.mrfilefixit.core.ui.components.ToolTopAppBar
import com.mrfilefixit.core.ui.format.formatIsoDate
import com.mrfilefixit.core.ui.format.formatNumber
import com.mrfilefixit.core.ui.preview.AllPreviews
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.core.ui.theme.MffTheme
import com.mrfilefixit.feature.exam.R

/** Callbacks for the catalog; a class so the screen stays previewable without a ViewModel. */
internal class ExamCatalogActions(
    val onSelect: (String) -> Unit,
    val onQuery: (String) -> Unit,
    val onCategory: (ExamCategory?) -> Unit,
    val onClearFilters: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * "Pick your exam": persistent search and category pills above a scrollable list of exam cards. The
 * disclaimer is the first list item and is not dismissible.
 */
@Composable
internal fun ExamCatalogScreen(
    state: ExamPickerUiState,
    selectedId: String?,
    actions: ExamCatalogActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ToolTopAppBar(stringResource(R.string.exam_title))
        Box(Modifier.weight(1f)) {
            when (state) {
                ExamPickerUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ExamPickerUiState.Error -> ErrorCard(state.error, actions.onRetry, Modifier.padding(Dimens.SpaceM))
                is ExamPickerUiState.Loaded -> Catalog(state, selectedId, actions)
            }
        }
        LocalAdSlots.current.Banner(BannerPlacement.EXAM_PICKER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Catalog(state: ExamPickerUiState.Loaded, selectedId: String?, actions: ExamCatalogActions) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            placeholder = { Text(stringResource(R.string.exam_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onQuery("") }) {
                        Icon(Icons.Filled.Close, stringResource(CoreR.string.action_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Dimens.ButtonCorner),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.ScreenPadding)
                .testTag("exam_search"),
        )
        PillRow(
            options = listOf<ExamCategory?>(null) + state.categories,
            selected = state.category,
            label = { it?.let { category -> categoryLabel(category) } ?: stringResource(R.string.exam_category_all) },
            onSelect = actions.onCategory,
        )
        val visible = state.visible
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("exam_list"),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding,
                end = Dimens.ScreenPadding,
                top = Dimens.SpaceS,
                bottom = Dimens.SpaceL,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            item(key = "disclaimer") { ExamDisclaimerCard() }
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.exam_count, visible.size, visible.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (visible.isEmpty()) {
                item(key = "empty") { NoMatches(state.query, actions.onClearFilters) }
            }
            items(visible, key = { it.id }) { profile ->
                ExamCard(profile, selected = profile.id == selectedId, onClick = { actions.onSelect(profile.id) })
            }
        }
    }
}

@Composable
private fun NoMatches(query: String, onClear: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Text(
            if (query.isBlank()) {
                stringResource(R.string.exam_no_matches_category)
            } else {
                stringResource(R.string.exam_no_matches, query)
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.exam_clear_filters)) }
    }
}

@Composable
private fun ExamCard(profile: ExamProfile, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(Dimens.CardCorner),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .testTag("exam_card_${profile.id}"),
    ) {
        Column(Modifier.padding(Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.shortName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Tag(categoryLabel(profile.category), colors.secondaryContainer, colors.onSecondaryContainer)
            }
            Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                Tag(profile.conductingBody, colors.surfaceContainerHighest, colors.onSurface)
                VerificationTag(profile)
            }
            QuickSpecs(profile)
        }
    }
}

/** "Photo 20–50 KB", "Sign 10–20 KB", "Docs (PDF)": the numbers people check first. */
@Composable
private fun QuickSpecs(profile: ExamProfile) {
    val chips = buildList {
        profile.document(DocumentKind.PHOTO)?.let { add(stringResource(R.string.exam_chip_photo, kbRange(it))) }
        profile.document(DocumentKind.SIGNATURE)?.let { add(stringResource(R.string.exam_chip_sign, kbRange(it))) }
        val extra = profile.documents.count { it.kind != DocumentKind.PHOTO && it.kind != DocumentKind.SIGNATURE }
        if (extra > 0) add(pluralStringResource(R.plurals.exam_chip_extra, extra, extra))
        if (profile.certificates.isNotEmpty()) {
            add(stringResource(R.string.exam_chip_docs, certificateFormats(profile.certificates)))
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        chips.forEach { Tag(it, MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface) }
    }
}

/** Small non-interactive label. Not a chip: TalkBack would announce a chip as a (disabled) button. */
@Composable
internal fun Tag(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(Dimens.SpaceS)) {
        Row(
            Modifier.padding(horizontal = Dimens.SpaceS, vertical = Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(TAG_ICON.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Honest labelling: "Checked on official page, <date>" only when a maintainer checked it; otherwise community. */
@Composable
internal fun VerificationTag(profile: ExamProfile) {
    val colors = MaterialTheme.colorScheme
    when (profile.verification) {
        VerificationStatus.VERIFIED -> Tag(
            stringResource(CoreR.string.verification_verified, formatIsoDate(profile.lastVerified.orEmpty())),
            colors.primaryContainer,
            colors.onPrimaryContainer,
            Icons.Filled.Verified,
        )
        VerificationStatus.COMMUNITY -> Tag(
            stringResource(CoreR.string.verification_community),
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
            Icons.Filled.Groups,
        )
    }
}

@Composable
internal fun kbRange(spec: DocumentSpec): String =
    stringResource(CoreR.string.range_kb, formatNumber(spec.minKb), formatNumber(spec.maxKb))

@Composable
internal fun kbRange(spec: CertificateSpec): String = if (spec.minKb == 0) {
    stringResource(R.string.exam_up_to_kb, formatNumber(spec.maxKb))
} else {
    stringResource(CoreR.string.range_kb, formatNumber(spec.minKb), formatNumber(spec.maxKb))
}

/** Format names stay in Latin script, as portals show them. */
internal fun certificateFormats(certificates: List<CertificateSpec>): String =
    certificates.flatMap { it.formats }.distinct().sorted().joinToString(" / ") {
        when (it) {
            CertificateFormat.PDF -> "PDF"
            CertificateFormat.JPEG -> "JPG"
        }
    }

@Composable
internal fun categoryLabel(category: ExamCategory): String = stringResource(
    when (category) {
        ExamCategory.SSC -> R.string.exam_category_SSC
        ExamCategory.UPSC -> R.string.exam_category_UPSC
        ExamCategory.BANKING -> R.string.exam_category_BANKING
        ExamCategory.RAILWAYS -> R.string.exam_category_RAILWAYS
        ExamCategory.DEFENCE -> R.string.exam_category_DEFENCE
        ExamCategory.ENTRANCE -> R.string.exam_category_ENTRANCE
        ExamCategory.TEACHING -> R.string.exam_category_TEACHING
        ExamCategory.STATE_PSC -> R.string.exam_category_STATE_PSC
        ExamCategory.OTHER -> R.string.exam_category_OTHER
    },
)

private const val TAG_ICON = 16

/** Sample data for previews and UI tests. */
internal val PreviewProfiles = listOf(
    ExamProfile(
        id = "ibps-po",
        shortName = "IBPS PO",
        name = "Probationary Officer / Management Trainee",
        category = ExamCategory.BANKING,
        conductingBody = "Institute of Banking Personnel Selection",
        sourceUrl = "https://www.ibps.in/",
        lastVerified = null,
        verification = VerificationStatus.COMMUNITY,
        documents = listOf(
            DocumentSpec(
                DocumentKind.PHOTO,
                setOf(ImageFormat.JPEG),
                20,
                50,
                IntBounds.exactly(200),
                IntBounds.exactly(230),
                200,
                230,
                background = "Light, preferably white",
            ),
            DocumentSpec(
                DocumentKind.SIGNATURE,
                setOf(ImageFormat.JPEG),
                10,
                20,
                IntBounds.exactly(140),
                IntBounds.exactly(60),
                140,
                60,
                ink = "Black",
            ),
            DocumentSpec(
                DocumentKind.LEFT_THUMB,
                setOf(ImageFormat.JPEG),
                20,
                50,
                IntBounds.exactly(240),
                IntBounds.exactly(240),
                240,
                240,
            ),
        ),
    ),
    ExamProfile(
        id = "gate",
        shortName = "GATE",
        name = "Graduate Aptitude Test in Engineering",
        category = ExamCategory.ENTRANCE,
        conductingBody = "IIT Madras",
        sourceUrl = "https://gate2027.iitm.ac.in/",
        lastVerified = "2026-10-24",
        verification = VerificationStatus.VERIFIED,
        documents = listOf(
            DocumentSpec(
                DocumentKind.PHOTO,
                setOf(ImageFormat.JPEG),
                5,
                600,
                IntBounds(200, 530),
                IntBounds(260, 690),
                420,
                540,
            ),
        ),
        certificates = listOf(CertificateSpec("Category certificate", setOf(CertificateFormat.PDF), 50, 300)),
    ),
)

private val NoOpCatalogActions = ExamCatalogActions({}, {}, {}, {}, {})

@AllPreviews
@Composable
private fun ExamCatalogPreview() {
    MffTheme(dynamicColor = false) {
        ExamCatalogScreen(ExamPickerUiState.Loaded(PreviewProfiles), selectedId = "ibps-po", NoOpCatalogActions)
    }
}
