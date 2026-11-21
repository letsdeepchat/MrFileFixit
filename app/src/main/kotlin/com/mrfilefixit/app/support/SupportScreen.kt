package com.mrfilefixit.app.support

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.mrfilefixit.app.R
import com.mrfilefixit.core.domain.support.Donation
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.theme.Dimens
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional support for the developer: UPI (QR code and UPI ID, paid from inside the user's own UPI app) and
 * PayPal.Me. No Google Play Billing, so the Play listing shows no "In-app purchases" label. Every tool stays free,
 * and nothing here unlocks anything.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val say: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

    ToolScaffold(
        title = stringResource(R.string.support_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.testTag("support"),
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = Dimens.CardMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.support_intro), style = MaterialTheme.typography.bodyLarge)
                }
                UpiCard(say)
                PayPalCard(say)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.support_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.support_thanks),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * UPI: QR code, UPI ID and three actions that always work with a personal UPI ID: copy the ID, save the QR code to
 * the gallery, or share it. The user then pays inside GPay, PhonePe or Paytm (paste the ID, or Scan QR → upload from
 * gallery). There is no `upi://pay` button: GPay and PhonePe reject such links to a personal ID with a false
 * "bank limit exceeded" error (see [Donation]).
 */
@Composable
private fun UpiCard(say: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.support_copied)
    val saved = stringResource(R.string.support_qr_saved)
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val failed = stringResource(CoreR.string.error_unexpected)
    // Android 9 has no MediaStore RELATIVE_PATH without a storage permission, so it saves through "Save as".
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PNG)) { uri ->
        if (uri != null) scope.launch { say(if (writeQr(context, uri)) saved else failed) }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.support_upi_title))
        Text(stringResource(R.string.support_upi_scan), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.upi_qr),
                contentDescription = stringResource(R.string.support_qr_cd, Donation.PAYEE_NAME),
                modifier = Modifier
                    .size(QR_SIZE.dp)
                    .clip(RoundedCornerShape(Dimens.ButtonCorner))
                    // QR codes need a light quiet zone to scan, also in dark theme.
                    .background(Color.White)
                    .testTag("support_qr"),
            )
        }
        Text(
            stringResource(R.string.support_payee, Donation.PAYEE_NAME),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.support_upi_id, Donation.UPI_ID),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UPI ID", Donation.UPI_ID)))
                    say(copied)
                }
            },
            shape = RoundedCornerShape(Dimens.ButtonCorner),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.PrimaryButtonHeight)
                .testTag("support_copy_upi"),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null)
            Text(stringResource(R.string.support_copy_upi), Modifier.padding(start = Dimens.SpaceS))
        }
        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    scope.launch { say(if (saveQrToGallery(context)) saved else failed) }
                } else {
                    saveAs.launch(QR_FILE_NAME)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("support_save_qr"),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text(stringResource(R.string.support_save_qr), Modifier.padding(start = Dimens.SpaceS))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val uri = exportQr(context)
                    if (uri == null) say(failed) else context.shareFile(uri, PNG, chooser)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("support_share_qr"),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text(stringResource(R.string.support_share_qr), Modifier.padding(start = Dimens.SpaceS))
        }
        Text(
            stringResource(R.string.support_upi_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PayPalCard(say: (String) -> Unit) {
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.support_no_browser)
    SectionCard(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.support_paypal_title))
        Text(stringResource(R.string.support_paypal_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = { if (!openLink(context, Donation.payPalUri())) say(noBrowser) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("support_paypal"),
        ) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Text(stringResource(R.string.support_paypal_button), Modifier.padding(start = Dimens.SpaceS))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Opens [url] in the user's browser. False when there is none. */
private fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Copies the QR image into the shareable cache folder and returns its content URI, or null on failure.
 * The PNG is read byte for byte from `drawable-nodpi`, which is never scaled, so it is the exact verified image.
 */
@SuppressLint("ResourceType")
private suspend fun exportQr(context: Context): String? = withContext(Dispatchers.IO) {
    try {
        val file = File(context.cacheDir, "jobs/support/upi_qr.png").apply { parentFile?.mkdirs() }
        context.resources.openRawResource(R.drawable.upi_qr).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Saves the QR code as a PNG in Pictures/MrFileFixit through MediaStore (Android 10+, no permission needed), so
 * users can pick it from GPay / PhonePe → Scan QR → upload from gallery. True when it was written.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun saveQrToGallery(context: Context): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, QR_FILE_NAME)
        put(MediaStore.Images.Media.MIME_TYPE, PNG)
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MrFileFixit")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = runCatching {
        resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }.getOrNull() ?: return@withContext false
    val ok = writeQrNow(context, uri)
    if (ok) {
        runCatching {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    } else {
        runCatching { resolver.delete(uri, null, null) }
    }
    ok
}

private suspend fun writeQr(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    writeQrNow(context, uri)
}

/**
 * Writes the bundled QR code to [uri] as a crisp PNG: scaled up [QR_EXPORT_SCALE]× with nearest-neighbour sampling,
 * so modules stay sharp, keeping the image's white quiet zone.
 */
@SuppressLint("ResourceType")
private fun writeQrNow(context: Context, uri: Uri): Boolean {
    val source = context.resources.openRawResource(R.drawable.upi_qr).use { BitmapFactory.decodeStream(it) }
        ?: return false
    val big = source.scale(source.width * QR_EXPORT_SCALE, source.height * QR_EXPORT_SCALE, filter = false)
    return try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            big.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        } ?: false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    } finally {
        if (big !== source) big.recycle()
        source.recycle()
    }
}

private const val QR_SIZE = 220
private const val QR_EXPORT_SCALE = 2
private const val PNG_QUALITY = 100
private const val QR_FILE_NAME = "MrFileFixit_UPI_QR.png"
private const val PNG = "image/png"
