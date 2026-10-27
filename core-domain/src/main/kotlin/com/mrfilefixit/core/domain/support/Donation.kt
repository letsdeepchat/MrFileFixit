package com.mrfilefixit.core.domain.support

/**
 * Optional donations to the developer. The app has no payment code and no network code: these are only links
 * handed to the user's own UPI app or browser, which do the payment. Nothing about a donation is recorded.
 */
object Donation {
    const val UPI_ID = "deepakchaudhari705-1@okaxis"
    const val PAYEE_NAME = "Deepak Chaudhari"
    const val PAYPAL_URL = "https://paypal.me/MrFileFixit"
    const val DEFAULT_NOTE = "Support Mr File Fixit"

    /** Suggested amounts in rupees. The user can always pay any amount, including none of these. */
    val suggestedRupees = listOf(51, 101, 251, 501)

    /**
     * What the bundled QR code (`upi_qr.png`) encodes: [upiUri] with no amount and no note, so any UPI app can
     * scan it from a second phone or a screenshot. Regenerate the image if this changes.
     */
    const val QR_PAYLOAD = "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR"

    /** Upper bound for a prefilled amount; anything else is left for the user to type in their UPI app. */
    const val MAX_RUPEES = 100_000

    /**
     * A `upi://pay` link (NPCI UPI linking specification) for any UPI app. [amountRupees] prefills the amount;
     * null leaves it to the user.
     */
    fun upiUri(amountRupees: Int? = null, note: String = DEFAULT_NOTE): String = buildString {
        append("upi://pay?pa=").append(encode(UPI_ID))
        append("&pn=").append(encode(PAYEE_NAME))
        if (amountRupees != null && amountRupees in 1..MAX_RUPEES) append("&am=").append(amountRupees).append(".00")
        append("&cu=INR")
        val cleanNote = note.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(MAX_NOTE)
        if (cleanNote.isNotEmpty()) append("&tn=").append(encode(cleanNote))
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
    private const val BYTE = 0xFF
    private const val ASCII = 0x80
    private const val NIBBLE = 4
    private const val LOW_NIBBLE = 0x0F
    private const val UNRESERVED = "-._~@"
    private const val HEX = "0123456789ABCDEF"
}
