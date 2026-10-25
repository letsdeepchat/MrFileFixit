package com.mrfilefixit.feature.resume.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrfilefixit.core.domain.resume.ResumeEdits
import com.mrfilefixit.core.domain.resume.ResumeEntry
import com.mrfilefixit.core.domain.resume.ResumeSection
import com.mrfilefixit.core.domain.resume.SectionKind
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.theme.Dimens
import com.mrfilefixit.feature.resume.R

/**
 * One section: its title with move, hide and delete buttons, then the editor for its kind. Bullets and
 * skills are stored exactly as typed (one item per line) so the field never rewrites itself mid-word.
 */
@Composable
internal fun SectionEditor(
    section: ResumeSection,
    isFirst: Boolean,
    isLast: Boolean,
    actions: ResumeEditorActions,
    modifier: Modifier = Modifier,
) {
    fun update(transform: (ResumeSection) -> ResumeSection) =
        actions.onEdit { ResumeEdits.updateSection(it, section.id, transform) }
    SectionCard(modifier.testTag("resume_section_${section.kind.name.lowercase()}")) {
        EditField(
            section.title,
            { v -> update { it.copy(title = v) } },
            stringResource(R.string.resume_section_title),
            supporting = if (section.visible) null else stringResource(R.string.resume_section_hidden),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            ActionIcon(Icons.Filled.ArrowUpward, stringResource(R.string.resume_section_up), enabled = !isFirst) {
                actions.onEdit { ResumeEdits.moveSection(it, section.id, -1) }
            }
            ActionIcon(Icons.Filled.ArrowDownward, stringResource(R.string.resume_section_down), enabled = !isLast) {
                actions.onEdit { ResumeEdits.moveSection(it, section.id, 1) }
            }
            ActionIcon(
                if (section.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                stringResource(if (section.visible) R.string.resume_section_hide else R.string.resume_section_show),
            ) { update { it.copy(visible = !it.visible) } }
            ActionIcon(Icons.Filled.Delete, stringResource(R.string.resume_section_delete)) {
                actions.onEdit { ResumeEdits.removeSection(it, section.id) }
            }
        }
        when (section.kind) {
            SectionKind.SUMMARY -> EditField(
                section.text,
                { v -> update { it.copy(text = v) } },
                stringResource(R.string.resume_summary_text),
                singleLine = false,
                supporting = stringResource(R.string.resume_summary_hint),
            )
            SectionKind.SKILLS -> SkillsEditor(section, actions)
            else -> EntriesEditor(section, actions)
        }
    }
}

@Composable
private fun SkillsEditor(section: ResumeSection, actions: ResumeEditorActions) {
    section.entries.forEachIndexed { index, entry ->
        EntryCard(section, entry, index, actions) { change ->
            EditField(entry.title, { v -> change { it.copy(title = v) } }, stringResource(R.string.resume_skill_group))
            EditField(
                entry.bullets.joinToString("\n"),
                { v -> change { it.copy(bullets = v.split('\n')) } },
                stringResource(R.string.resume_skill_list),
                singleLine = false,
            )
        }
    }
    AddButton(stringResource(R.string.resume_add_skill_group)) {
        actions.onEdit { ResumeEdits.addEntry(it, section.id, actions.newId()) }
    }
}

@Composable
private fun EntriesEditor(section: ResumeSection, actions: ResumeEditorActions) {
    val titleLabel = TemplateText.entryTitle(section.kind)?.let { stringResource(it) }.orEmpty()
    val subtitleLabel = TemplateText.entrySubtitle(section.kind)?.let { stringResource(it) }.orEmpty()
    section.entries.forEachIndexed { index, entry ->
        EntryCard(section, entry, index, actions) { change ->
            EditField(entry.title, { v -> change { it.copy(title = v) } }, titleLabel)
            EditField(entry.subtitle, { v -> change { it.copy(subtitle = v) } }, subtitleLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                EditField(
                    entry.location,
                    { v -> change { it.copy(location = v) } },
                    stringResource(R.string.resume_location_short),
                    Modifier.weight(1f),
                )
                EditField(
                    entry.dates,
                    { v -> change { it.copy(dates = v) } },
                    stringResource(R.string.resume_dates),
                    Modifier.weight(1f),
                )
            }
            EditField(
                entry.bullets.joinToString("\n"),
                { v -> change { it.copy(bullets = v.split('\n')) } },
                stringResource(R.string.resume_bullets),
                singleLine = false,
            )
        }
    }
    AddButton(stringResource(R.string.resume_add_entry)) {
        actions.onEdit { ResumeEdits.addEntry(it, section.id, actions.newId()) }
    }
}

/** An entry in a nested outlined card, with its own move and remove buttons. */
@Composable
private fun EntryCard(
    section: ResumeSection,
    entry: ResumeEntry,
    index: Int,
    actions: ResumeEditorActions,
    content: @Composable (change: ((ResumeEntry) -> ResumeEntry) -> Unit) -> Unit,
) {
    val change: ((ResumeEntry) -> ResumeEntry) -> Unit = { transform ->
        actions.onEdit { ResumeEdits.updateEntry(it, section.id, entry.id, transform) }
    }
    OutlinedCard(
        shape = RoundedCornerShape(Dimens.ButtonCorner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            content(change)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ActionIcon(Icons.Filled.ArrowUpward, stringResource(R.string.resume_entry_up), enabled = index > 0) {
                    actions.onEdit { ResumeEdits.moveEntry(it, section.id, entry.id, -1) }
                }
                ActionIcon(
                    Icons.Filled.ArrowDownward,
                    stringResource(R.string.resume_entry_down),
                    enabled = index < section.entries.lastIndex,
                ) { actions.onEdit { ResumeEdits.moveEntry(it, section.id, entry.id, 1) } }
                ActionIcon(Icons.Filled.Delete, stringResource(R.string.resume_entry_delete)) {
                    actions.onEdit { ResumeEdits.removeEntry(it, section.id, entry.id) }
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = Dimens.MinTouchTarget)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(label, Modifier.padding(start = Dimens.SpaceXs))
    }
}
