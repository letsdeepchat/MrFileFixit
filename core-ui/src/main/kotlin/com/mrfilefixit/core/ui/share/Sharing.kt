package com.mrfilefixit.core.ui.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/**
 * Opens the share sheet for one FileProvider URI with a read-only grant limited to that URI.
 * The grant goes through ClipData so it reaches the chosen target, and nothing else is granted.
 */
fun Context.shareFile(uri: String, mimeType: String, chooserTitle: String) {
    val contentUri = Uri.parse(uri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, contentUri)
        clipData = ClipData.newRawUri(null, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Same as [shareFile] for several files of one type. */
fun Context.shareFiles(uris: List<String>, mimeType: String, chooserTitle: String) {
    if (uris.size == 1) return shareFile(uris.single(), mimeType, chooserTitle)
    val parsed = ArrayList(uris.map(Uri::parse))
    val clip = ClipData.newRawUri(null, parsed.first()).apply { parsed.drop(1).forEach { addItem(ClipData.Item(it)) } }
    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, parsed)
        clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Finds the hosting Activity from a Compose context (needed for ads). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens the share sheet for plain text (OCR output). Nothing is granted. */
fun Context.shareText(text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, chooserTitle))
}
