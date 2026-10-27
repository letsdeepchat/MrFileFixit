package com.mrfilefixit.core.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mrfilefixit.core.ui.R

/**
 * Back for an inner screen: the top bar arrow and the system back gesture both run [onBack], but while
 * [unsaved] is true they first ask whether to leave and lose the result. Returns the action for the top bar
 * button. While [enabled] is false the system back is left to the next handler (the navigation host).
 */
@Composable
fun rememberGuardedBack(unsaved: Boolean, onBack: () -> Unit, enabled: Boolean = true): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val currentUnsaved by rememberUpdatedState(unsaved)
    val currentOnBack by rememberUpdatedState(onBack)
    val request = remember {
        {
            if (currentUnsaved) asking = true else currentOnBack()
        }
    }
    BackHandler(enabled = enabled, onBack = request)
    if (asking) {
        DiscardDialog(
            onLeave = {
                asking = false
                currentOnBack()
            },
            onStay = { asking = false },
        )
    }
    return request
}

/** Asks before unsaved work is thrown away. */
@Composable
fun DiscardDialog(onLeave: () -> Unit, onStay: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = {
            TextButton(onClick = onLeave, modifier = Modifier.testTag("discard_leave")) {
                Text(stringResource(R.string.discard_leave))
            }
        },
        dismissButton = {
            TextButton(onClick = onStay, modifier = Modifier.testTag("discard_stay")) {
                Text(stringResource(R.string.discard_stay))
            }
        },
        modifier = modifier.testTag("discard_dialog"),
    )
}
