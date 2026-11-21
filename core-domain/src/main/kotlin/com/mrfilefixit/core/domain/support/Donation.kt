package com.mrfilefixit.core.domain.support

/**
 * Optional donations to the developer. The app has no payment code and no network code: the user pays inside their
 * own UPI app (by pasting the UPI ID or scanning the QR code) or on PayPal in the browser. Nothing is recorded.
 *
 * There is deliberately no "Pay with a UPI app" button. [UPI_ID] is a personal (person-to-person) UPI ID, and
 * Google Pay and PhonePe reject `upi://pay` links that a third-party app hands them for a personal ID, showing a
 * misleading "You have exceeded the bank limit" error. Paying from inside the UPI app has no such limit.
 */
object Donation {
    const val UPI_ID = "deepakchaudhari705-1@okaxis"
    const val PAYEE_NAME = "Deepak Chaudhari"
    const val PAYPAL_URL = "https://paypal.me/MrFileFixit"

    /**
     * What the bundled QR code (`upi_qr.png`) encodes: the minimal personal UPI payload, payee address, name and
     * currency only. No `am`, `tn`, `tr`, `tid` or `mc`, which a personal ID cannot carry. Regenerate the image if
     * this changes.
     */
    const val QR_PAYLOAD = "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR"

    /** The standard personal UPI link for [UPI_ID]: `pa`, `pn` and `cu=INR`, nothing else. */
    fun upiUri(): String = "upi://pay?pa=" + encode(UPI_ID) + "&pn=" + encode(PAYEE_NAME) + "&cu=INR"

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

    private const val BYTE = 0xFF
    private const val ASCII = 0x80
    private const val NIBBLE = 4
    private const val LOW_NIBBLE = 0x0F
    private const val UNRESERVED = "-._~@"
    private const val HEX = "0123456789ABCDEF"
}
