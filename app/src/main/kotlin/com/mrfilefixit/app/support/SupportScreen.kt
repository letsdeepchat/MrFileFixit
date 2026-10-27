package com.mrfilefixit.app.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.core.net.toUri
import com.mrfilefixit.app.R
import com.mrfilefixit.core.domain.support.Donation
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.PrimaryActionButton
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
 * Optional donations: UPI for India (QR code, UPI ID and a `upi://pay` link to the user's own UPI app) and
 * PayPal.Me for everyone else. The app takes no part in the payment: it only opens another app or the browser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var amount by rememberSaveable { mutableStateOf<Int?>(null) }
    val copied = stringResource(R.string.support_copied)
    val noUpiApp = stringResource(R.string.support_no_upi_app)
    val noBrowser = stringResource(R.string.support_no_browser)
    val chooseUpi = stringResource(R.string.support_choose_upi)
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val shareFailed = stringResource(CoreR.string.error_unexpected)
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UPI ID", Donation.UPI_ID)))
                                    say(copied)
                                }
                            },
                            modifier = Modifier.testTag("support_copy_upi"),
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text(stringResource(R.string.support_copy_upi), Modifier.padding(start = Dimens.SpaceS))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val uri = exportQr(context)
                                    if (uri == null) say(shareFailed) else context.shareFile(uri, PNG, chooser)
                                }
                            },
                            modifier = Modifier.testTag("support_share_qr"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Text(stringResource(R.string.support_share_qr), Modifier.padding(start = Dimens.SpaceS))
                        }
                    }
                    Text(stringResource(R.string.support_amount), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        FilterChip(
                            selected = amount == null,
                            onClick = { amount = null },
                            label = { Text(stringResource(R.string.support_amount_any)) },
                        )
                        Donation.suggestedRupees.forEach { rupees ->
                            FilterChip(
                                selected = amount == rupees,
                                onClick = { amount = rupees },
                                label = { Text(stringResource(R.string.support_amount_value, rupees)) },
                                modifier = Modifier.testTag("support_amount_$rupees"),
                            )
                        }
                    }
                    PrimaryActionButton(
                        text = stringResource(R.string.support_pay_upi),
                        onClick = { if (!openUpi(context, Donation.upiUri(amount), chooseUpi)) say(noUpiApp) },
                        modifier = Modifier.testTag("support_pay_upi"),
                    )
                }

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

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Lets the user pick a UPI app for [upiUri]. False when none is installed. */
private fun openUpi(context: Context, upiUri: String, chooserTitle: String): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, upiUri.toUri())
    val apps = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (apps.isEmpty()) return false
    return try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Opens [url] in the user's browser. False when there is none. */
private fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Copies the QR image into the shareable cache folder and returns its content URI, or null on failure. */
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

private const val QR_SIZE = 220
private const val PNG = "image/png"
