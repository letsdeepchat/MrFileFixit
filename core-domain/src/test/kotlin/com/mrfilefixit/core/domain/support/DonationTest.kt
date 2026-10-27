package com.mrfilefixit.core.domain.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DonationTest {
    @Test
    fun `upi link pays the developer's UPI id in rupees with no amount by default`() {
        assertEquals(
            "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR&tn=Support%20Mr%20File%20Fixit",
            Donation.upiUri(),
        )
    }

    @Test
    fun `a suggested amount is prefilled with paise`() {
        assertTrue(Donation.upiUri(101).contains("&am=101.00&cu=INR"))
    }

    @Test
    fun `impossible amounts are left for the user to type`() {
        assertFalse(Donation.upiUri(0).contains("am="))
        assertFalse(Donation.upiUri(-5).contains("am="))
        assertFalse(Donation.upiUri(Donation.MAX_RUPEES + 1).contains("am="))
    }

    @Test
    fun `the note cannot inject extra parameters`() {
        val uri = Donation.upiUri(note = "thanks&pa=attacker@bank")
        assertEquals(1, Regex("pa=").findAll(uri).count())
        assertTrue(uri.endsWith("&tn=thankspaattackerbank"))
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
