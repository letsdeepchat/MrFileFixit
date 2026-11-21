package com.mrfilefixit.core.ui.permission

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mrfilefixit.core.ui.R
import com.mrfilefixit.core.ui.share.findActivity

/** The runtime permissions the app asks for, each right before the tool that needs it. */
enum class AppPermission {
    /** The camera, before the document scanner opens. */
    CAMERA,

    /** The user's photos, before an image picker opens: READ_MEDIA_IMAGES on Android 13+, else storage read. */
    PHOTOS,
    ;

    /**
     * What to ask Android for. Photos on Android 14+ also asks for READ_MEDIA_VISUAL_USER_SELECTED, so "Select
     * photos" (partial access) is offered and counts as allowed.
     */
    internal val manifestPermissions: Array<String>
        get() = when (this) {
            CAMERA -> arrayOf(Manifest.permission.CAMERA)
            PHOTOS -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
}

/** True when [permission] is granted; for photos, full or partial ("Select photos") access both count. */
fun Context.hasPermission(permission: AppPermission): Boolean = permission.manifestPermissions.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Returns an action that asks for [permission] with the Android dialog and runs [onGranted] once it is granted
 * (straight away when it already is).
 *
 * If the user says no, an explanation appears with "Open settings" (for when Android no longer shows its own
 * dialog). For [AppPermission.PHOTOS] the system photo picker still works without the permission, so the
 * explanation also offers "Continue" which runs [onGranted] anyway; after that choice the app stops asking for the
 * rest of the session. For [AppPermission.CAMERA] the action is not run without the permission.
 */
@Composable
fun rememberPermissionGate(permission: AppPermission, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val proceed by rememberUpdatedState(onGranted)
    var denied by rememberSaveable(permission) { mutableStateOf(false) }
    var continueWithout by rememberSaveable(permission) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) proceed() else denied = true
    }
    if (denied) {
        PermissionDeniedDialog(
            permission = permission,
            onOpenSettings = {
                denied = false
                openAppSettings(context)
            },
            onContinue = if (permission == AppPermission.PHOTOS) {
                {
                    denied = false
                    continueWithout = true
                    proceed()
                }
            } else {
                null
            },
            onDismiss = { denied = false },
        )
    }
    return {
        when {
            context.hasPermission(permission) || continueWithout -> proceed()
            else -> launcher.launch(permission.manifestPermissions)
        }
    }
}

@Composable
private fun PermissionDeniedDialog(
    permission: AppPermission,
    onOpenSettings: () -> Unit,
    onContinue: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (permission) {
        AppPermission.CAMERA -> R.string.permission_camera_title to R.string.permission_camera_body
        AppPermission.PHOTOS -> R.string.permission_photos_title to R.string.permission_photos_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("permission_open_settings")) {
                Text(stringResource(R.string.permission_open_settings))
            }
        },
        dismissButton = {
            if (onContinue != null) {
                TextButton(onClick = onContinue, modifier = Modifier.testTag("permission_continue")) {
                    Text(stringResource(R.string.permission_continue_picker))
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("permission_not_now")) {
                    Text(stringResource(R.string.permission_not_now))
                }
            }
        },
        modifier = Modifier.testTag("permission_denied_${permission.name.lowercase()}"),
    )
}

/** Opens this app's page in Android settings, where the user can allow the permission. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
