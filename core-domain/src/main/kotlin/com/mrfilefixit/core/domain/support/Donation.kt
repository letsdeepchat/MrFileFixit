package com.mrfilefixit.core.domain.support

/**
 * Optional donations to the developer. The app has no payment code and no network code: these are only links
 * handed to the user's own UPI app or browser, which do the payment. Nothing about a donation is recorded.
 */
object Donation {
    const val UPI_ID = "deepakchaudhari705-1@okaxis"
    const val PAYEE_NAME = "Deepak Chaudhari"
    const val PAYPAL_URL = "https://paypal.me/MrFileFixit"

    /** Suggested amounts in rupees. The user can always pay any amount, including none of these. */
    val suggestedRupees = listOf(49, 99, 199, 499)

    /**
     * What the bundled QR code (`upi_qr.png`) encodes: [upiUri] with no amount and no note, so any UPI app can
     * scan it from a second phone or a screenshot. Regenerate the image if this changes.
     */
    const val QR_PAYLOAD = "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR"

    /** Upper bound for a prefilled amount; anything else is left for the user to type in their UPI app. */
    const val MAX_RUPEES = 100_000

    /**
     * A `upi://pay` link (NPCI UPI linking specification) for any UPI app: `pa`, `pn`, then `am` with paise for a
     * preset and `cu=INR`. [amountRupees] null ("Any amount") leaves out `am`, so the UPI app asks for the amount.
     * No note by default: some UPI apps decline person-to-person links that carry one.
     */
    fun upiUri(amountRupees: Int? = null, note: String = ""): String = buildString {
        append("upi://pay?pa=").append(encode(UPI_ID))
        append("&pn=").append(encode(PAYEE_NAME))
        if (amountRupees != null && amountRupees in 1..MAX_RUPEES) append("&am=").append(amountRupees).append(".00")
        append("&cu=INR")
        val cleanNote = note.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(MAX_NOTE)
        if (cleanNote.isNotEmpty()) append("&tn=").append(encode(cleanNote))
    }

    /**
     * The rupees typed in the custom amount field, or null when it is empty, not a whole number or outside
     * 1..[MAX_RUPEES]. Spaces, commas and a leading ₹ are ignored ("₹1,000" is 1000).
     */
    fun parseRupees(text: String): Int? {
        val digits = text.trim().removePrefix("₹").filterNot { it == ',' || it.isWhitespace() }
        if (digits.isEmpty() || !digits.all { it in '0'..'9' } || digits.length > MAX_DIGITS) return null
        return digits.toInt().takeIf { it in 1..MAX_RUPEES }
    }

    /**
     * What the UPI app reported back. Apps return the NPCI response string (for example
     * `txnId=...&responseCode=00&Status=SUCCESS&txnRef=...`) in the `response` extra; many return nothing at all,
     * so [UpiOutcome.UNKNOWN] is common and must not be read as a failure.
     */
    fun parseUpiResponse(response: String?): UpiOutcome {
        val status = response.orEmpty().split('&')
            .map { it.substringBefore('=').trim() to it.substringAfter('=', "").trim() }
            .firstOrNull { (key, _) -> key.equals("status", ignoreCase = true) }
            ?.second?.uppercase()
        return when (status) {
            "SUCCESS" -> UpiOutcome.SUCCESS
            "SUBMITTED", "PENDING" -> UpiOutcome.PENDING
            "FAILURE", "FAILED" -> UpiOutcome.FAILED
            else -> UpiOutcome.UNKNOWN
        }
    }

    /** The PayPal.Me page; PayPal asks for the amount and currency. */
    fun payPalUri(): String = PAYPAL_URL

    /** RFC 3986 percent-encoding of UTF-8 bytes (spaces become %20, which every UPI app accepts). */
    internal fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt() and BYTE
            val ch = c.toChar()
            if (c < ASCII && (ch.isLetterOrDigit() || ch in UNRESERVED)) {
                append(ch)
            } else {
                append('%').append(HEX[c shr NIBBLE]).append(HEX[c and LOW_NIBBLE])
            }
        }
    }

    private const val MAX_NOTE = 50
    private const val MAX_DIGITS = 6
    private const val BYTE = 0xFF
    private const val ASCII = 0x80
    private const val NIBBLE = 4
    private const val LOW_NIBBLE = 0x0F
    private const val UNRESERVED = "-._~@"
    private const val HEX = "0123456789ABCDEF"
}

/** The result a UPI app hands back after a payment attempt. */
enum class UpiOutcome { SUCCESS, PENDING, FAILED, UNKNOWN }
