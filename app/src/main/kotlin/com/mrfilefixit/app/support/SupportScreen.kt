package com.mrfilefixit.app.support

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mrfilefixit.app.BuildConfig
import com.mrfilefixit.app.R
import com.mrfilefixit.core.domain.support.Donation
import com.mrfilefixit.core.domain.support.UpiOutcome
import com.mrfilefixit.core.ui.R as CoreR
import com.mrfilefixit.core.ui.components.PrimaryActionButton
import com.mrfilefixit.core.ui.components.SectionCard
import com.mrfilefixit.core.ui.components.ToolScaffold
import com.mrfilefixit.core.ui.share.findActivity
import com.mrfilefixit.core.ui.share.shareFile
import com.mrfilefixit.core.ui.theme.Dimens
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Optional support for the developer. Tester builds offer UPI (QR code, UPI ID and a `upi://pay` link to the
 * user's own UPI app) and PayPal.Me; the Play build offers tips through Google Play Billing instead, as Play's
 * Payments policy requires. Every tool stays free either way, and nothing here unlocks anything.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var thanks by rememberSaveable { mutableStateOf(false) }
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
                if (BuildConfig.BILLING_ENABLED) PlayTipsCard(onThanks = { thanks = true }, say = say)
                if (BuildConfig.DONATIONS_ENABLED) {
                    UpiCard(onThanks = { thanks = true }, say = say)
                    PayPalCard(say)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val privacy =
                        if (BuildConfig.DONATIONS_ENABLED) R.string.support_privacy else R.string.support_privacy_play
                    Text(
                        stringResource(privacy),
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
    if (thanks) ThanksDialog(onDismiss = { thanks = false })
}

@Composable
private fun ThanksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
        title = { Text(stringResource(R.string.support_thanks_title)) },
        text = { Text(stringResource(R.string.support_thanks_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.support_thanks_ok)) } },
        modifier = Modifier.testTag("support_thanks_dialog"),
    )
}

/** Play build: the four tips at Play's prices in the user's currency, paid in Play's own purchase sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayTipsCard(onThanks: () -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf<List<TipOption>?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val pending = stringResource(R.string.support_play_pending)
    val failed = stringResource(R.string.support_play_failed)
    val currentThanks by rememberUpdatedState(onThanks)
    val currentSay by rememberUpdatedState(say)
    val tips = remember(context) {
        PlayTips(
            context,
            onOptions = { options = it },
            onEvent = { event ->
                when (event) {
                    TipEvent.THANKS -> currentThanks()
                    TipEvent.PENDING -> currentSay(pending)
                    TipEvent.FAILED -> currentSay(failed)
                    TipEvent.UNAVAILABLE -> unavailable = true
                }
            },
        )
    }
    DisposableEffect(tips) {
        tips.connect()
        onDispose { tips.close() }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.support_play_title))
        Text(stringResource(R.string.support_play_body), style = MaterialTheme.typography.bodyMedium)
        val loaded = options
        when {
            loaded != null -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                modifier = Modifier.fillMaxWidth(),
            ) {
                loaded.forEach { option ->
                    OutlinedButton(
                        onClick = { context.findActivity()?.let { tips.tip(it, option) } },
                        modifier = Modifier.testTag("support_${option.productId}"),
                    ) { Text(option.price) }
                }
            }
            unavailable -> Text(
                stringResource(R.string.support_play_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> CircularProgressIndicator(Modifier.size(PROGRESS_SIZE.dp))
        }
    }
}

/**
 * UPI: QR code, UPI ID, a preset or custom amount, and a `upi://pay` link to the user's UPI app. When the UPI app
 * reports the payment back (many do, in the NPCI response string), a success shows the thank-you.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpiCard(onThanks: () -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var preset by rememberSaveable { mutableStateOf<Int?>(null) }
    // Null while a preset or "Any amount" is chosen; the typed text while "Custom" is.
    var custom by rememberSaveable { mutableStateOf<String?>(null) }
    val customText = custom
    val amount = if (customText != null) Donation.parseRupees(customText) else preset
    val copied = stringResource(R.string.support_copied)
    val noUpiApp = stringResource(R.string.support_no_upi_app)
    val chooseUpi = stringResource(R.string.support_choose_upi)
    val chooser = stringResource(CoreR.string.share_chooser_title)
    val shareFailed = stringResource(CoreR.string.error_unexpected)
    val upiFailed = stringResource(R.string.support_upi_failed)
    val upiPending = stringResource(R.string.support_upi_pending)
    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val response = data?.getStringExtra("response") ?: data?.getStringExtra("Status")?.let { "Status=$it" }
        when (Donation.parseUpiResponse(response)) {
            UpiOutcome.SUCCESS -> onThanks()
            UpiOutcome.FAILED -> say(upiFailed)
            UpiOutcome.PENDING -> say(upiPending)
            UpiOutcome.UNKNOWN -> Unit
        }
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
                selected = customText == null && preset == null,
                onClick = {
                    preset = null
                    custom = null
                },
                label = { Text(stringResource(R.string.support_amount_any)) },
            )
            Donation.suggestedRupees.forEach { rupees ->
                FilterChip(
                    selected = customText == null && preset == rupees,
                    onClick = {
                        preset = rupees
                        custom = null
                    },
                    label = { Text(stringResource(R.string.support_amount_value, rupees)) },
                    modifier = Modifier.testTag("support_amount_$rupees"),
                )
            }
            FilterChip(
                selected = customText != null,
                onClick = { if (custom == null) custom = "" },
                label = { Text(stringResource(R.string.support_amount_custom)) },
                modifier = Modifier.testTag("support_amount_custom"),
            )
        }
        if (customText != null) {
            OutlinedTextField(
                value = customText,
                onValueChange = { text -> custom = text.filter(Char::isDigit).take(CUSTOM_DIGITS) },
                label = { Text(stringResource(R.string.support_amount_custom_label)) },
                prefix = { Text("₹") },
                singleLine = true,
                isError = customText.isNotEmpty() && amount == null,
                supportingText = {
                    Text(stringResource(R.string.support_amount_custom_hint, Donation.MAX_RUPEES))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("support_amount_custom_field"),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.support_pay_upi),
            enabled = customText == null || amount != null,
            onClick = {
                try {
                    upiLauncher.launch(upiIntent(context, Donation.upiUri(amount), chooseUpi))
                } catch (_: ActivityNotFoundException) {
                    say(noUpiApp)
                }
            },
            modifier = Modifier.testTag("support_pay_upi"),
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

/**
 * The intent that hands [upiUri] to the user's UPI app (GPay, PhonePe, Paytm, BHIM, ...), with a chooser when
 * several are installed. Launching it throws ActivityNotFoundException when nothing opens `upi://` links.
 *
 * The lookup uses no flags: several UPI apps do not mark their pay screen as a default handler, so
 * MATCH_DEFAULT_ONLY reported "no UPI app" on phones that had them. If the lookup finds nothing (a hidden
 * package), the link is still launched and only a real ActivityNotFoundException counts as "none installed".
 */
private fun upiIntent(context: Context, upiUri: String, chooserTitle: String): Intent {
    val intent = Intent(Intent.ACTION_VIEW, upiUri.toUri())

    @Suppress("DEPRECATION") // The flags overload is API 33+; 0 means the same on every version.
    val found = context.packageManager.queryIntentActivities(intent, 0).size
    return if (found > 1) Intent.createChooser(intent, chooserTitle) else intent
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

private const val QR_SIZE = 220
private const val PROGRESS_SIZE = 32
private const val CUSTOM_DIGITS = 6
private const val PNG = "image/png"
