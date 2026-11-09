package com.mrfilefixit.feature.exam.data

import com.mrfilefixit.core.domain.exam.CertificateFormat
import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExamRulesParserTest {
    private fun file(profileJson: String) = """{"schemaVersion":2,"profiles":[$profileJson]}"""

    private val document =
        """{"kind":"photo","formats":["jpeg"],"sizeKb":{"min":20,"max":50},"widthPx":{"min":200,"max":200},""" +
            """"heightPx":{"min":230,"max":230},"targetPx":{"width":200,"height":230}}"""

    private fun profile(
        id: String = "demo",
        verification: String = "community",
        lastVerified: String = "null",
        documents: String = document,
        composite: String = "",
        certificates: String = "",
    ) = """{"id":"$id","shortName":"Demo","name":"Demo exam","category":"OTHER","conductingBody":"Demo",""" +
        """"lastVerified":$lastVerified,"verification":"$verification",""" +
        """"documents":[$documents]$certificates$composite}"""

    @Test
    fun `parses a minimal profile with composite`() {
        val composite = ""","composite":{"document":$document,"photoFraction":0.7}"""
        val result = ExamRulesParser.parse(file(profile(composite = composite)))
        result as Result.Success
        val parsed = result.value.single()
        assertEquals(DocumentKind.PHOTO, parsed.documents.single().kind)
        assertEquals(0.7f, parsed.composite?.photoFraction)
    }

    @Test
    fun `parses certificates with an open lower bound`() {
        val certificates = ""","certificates":[{"name":"Category certificate","formats":["pdf"],""" +
            """"sizeKb":{"max":400}}]"""
        val result = ExamRulesParser.parse(file(profile(certificates = certificates)))
        result as Result.Success
        val certificate = result.value.single().certificates.single()
        assertEquals(setOf(CertificateFormat.PDF), certificate.formats)
        assertEquals(0..400, certificate.minKb..certificate.maxKb)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "verification=verified",
            "lastVerified=\"22-10-2026\"",
            "documents=",
            "unknownKey",
            "schema1",
            "verification=unverified",
            "certificateFormat=docx",
            "duplicateIds",
        ],
    )
    fun `rejects invalid rule files`(case: String) {
        val text = when (case) {
            "verification=verified" -> file(profile(verification = "verified"))
            "lastVerified=\"22-10-2026\"" -> file(profile(lastVerified = "\"22-10-2026\""))
            "documents=" -> file(profile(documents = ""))
            "unknownKey" -> file(profile().dropLast(1) + ""","extra":1}""")
            "schema1" -> file(profile()).replace("\"schemaVersion\":2", "\"schemaVersion\":1")
            "verification=unverified" -> file(profile(verification = "unverified"))
            "certificateFormat=docx" -> file(
                profile(certificates = ""","certificates":[{"name":"ID","formats":["docx"],"sizeKb":{"max":9}}]"""),
            )
            "duplicateIds" -> """{"schemaVersion":2,"profiles":[${profile()},${profile()}]}"""
            else -> error(case)
        }
        val result = ExamRulesParser.parse(text)
        assertTrue(result is Result.Failure, "Expected failure for $case")
        assertTrue((result as Result.Failure).error is AppError.Unexpected)
    }
}
