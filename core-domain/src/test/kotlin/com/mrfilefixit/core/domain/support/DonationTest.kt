package com.mrfilefixit.core.domain.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DonationTest {
    @Test
    fun `upi link pays the developer's UPI id in rupees with no amount by default`() {
        assertEquals(
            "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&cu=INR",
            Donation.upiUri(),
        )
    }

    @Test
    fun `the bundled QR payload is the plain link without amount or note`() {
        assertEquals(Donation.QR_PAYLOAD, Donation.upiUri())
    }

    @Test
    fun `every preset is prefilled exactly as NPCI expects`() {
        Donation.suggestedRupees.forEach { rupees ->
            assertEquals(
                "upi://pay?pa=deepakchaudhari705-1@okaxis&pn=Deepak%20Chaudhari&am=$rupees.00&cu=INR",
                Donation.upiUri(rupees),
            )
        }
    }

    @Test
    fun `a suggested amount is prefilled with paise`() {
        assertTrue(Donation.upiUri(99).contains("&am=99.00&cu=INR"))
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

    @Test
    fun `the suggested tiers are 49, 99, 199 and 499 rupees`() {
        assertEquals(listOf(49, 99, 199, 499), Donation.suggestedRupees)
    }

    @Test
    fun `a typed custom amount is read as whole rupees`() {
        assertEquals(150, Donation.parseRupees("150"))
        assertEquals(1000, Donation.parseRupees(" ₹1,000 "))
        assertEquals(null, Donation.parseRupees(""))
        assertEquals(null, Donation.parseRupees("0"))
        assertEquals(null, Donation.parseRupees("12.50"))
        assertEquals(null, Donation.parseRupees("-5"))
        assertEquals(null, Donation.parseRupees("99999999999"))
        assertEquals(null, Donation.parseRupees((Donation.MAX_RUPEES + 1).toString()))
    }

    @Test
    fun `the UPI app response is read from its Status field`() {
        assertEquals(
            UpiOutcome.SUCCESS,
            Donation.parseUpiResponse("txnId=AX1&responseCode=00&Status=SUCCESS&txnRef=1"),
        )
        assertEquals(UpiOutcome.SUCCESS, Donation.parseUpiResponse("status=success"))
        assertEquals(UpiOutcome.FAILED, Donation.parseUpiResponse("Status=FAILURE&responseCode=ZD"))
        assertEquals(UpiOutcome.PENDING, Donation.parseUpiResponse("Status=SUBMITTED"))
        assertEquals(UpiOutcome.UNKNOWN, Donation.parseUpiResponse(null))
        assertEquals(UpiOutcome.UNKNOWN, Donation.parseUpiResponse("nonsense"))
    }
}
