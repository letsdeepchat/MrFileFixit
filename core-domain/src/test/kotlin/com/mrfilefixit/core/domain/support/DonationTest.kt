package com.mrfilefixit.core.domain.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DonationTest {
    @Test
    fun `upi link is the minimal personal payload`() {
        assertEquals(
            "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR",
            Donation.upiUri(),
        )
    }

    @Test
    fun `the bundled QR payload is the plain link`() {
        assertEquals(Donation.QR_PAYLOAD, Donation.upiUri())
    }

    @Test
    fun `the QR payload carries no merchant or transaction fields`() {
        val params = Donation.QR_PAYLOAD.substringAfter('?').split('&').map { it.substringBefore('=') }
        assertEquals(listOf("pa", "pn", "cu"), params)
        listOf("am", "tn", "tr", "tid", "mc").forEach { assertFalse(it in params) }
    }

    @Test
    fun `encoding follows RFC 3986`() {
        assertEquals("a%20b%26c%3Dd", Donation.encode("a b&c=d"))
        assertEquals("%E0%A4%A6", Donation.encode("द"))
        assertEquals("x-y_z.~@", Donation.encode("x-y_z.~@"))
    }

    @Test
    fun `paypal goes to the PayPal Me page`() {
        assertEquals("https://paypal.me/MrFileFixit", Donation.payPalUri())
    }
}
