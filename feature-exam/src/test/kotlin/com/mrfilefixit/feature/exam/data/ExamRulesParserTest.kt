package com.mrfilefixit.feature.exam.data

import com.mrfilefixit.core.domain.exam.DocumentKind
import com.mrfilefixit.core.domain.result.AppError
import com.mrfilefixit.core.domain.result.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExamRulesParserTest {
    private fun file(profileJson: String) = """{"schemaVersion":1,"profiles":[$profileJson]}"""

    private val document =
        """{"kind":"photo","formats":["jpeg"],"sizeKb":{"min":20,"max":50},"widthPx":{"min":200,"max":200},""" +
            """"heightPx":{"min":230,"max":230},"targetPx":{"width":200,"height":230}}"""

    private fun profile(
        id: String = "demo",
        url: String = "https://example.org",
        verification: String = "unverified",
        lastVerified: String = "null",
        documents: String = document,
        composite: String = "",
    ) = """{"id":"$id","name":"Demo exam","category":"OTHER","conductingBody":"Demo","sourceUrl":"$url",""" +
        """"lastVerified":$lastVerified,"verification":"$verification","documents":[$documents]$composite}"""

    @Test
    fun `parses a minimal profile with composite`() {
        val composite = ""","composite":{"document":$document,"photoFraction":0.7}"""
        val result = ExamRulesParser.parse(file(profile(composite = composite)))
        result as Result.Success
        val parsed = result.value.single()
        assertEquals(DocumentKind.PHOTO, parsed.documents.single().kind)
        assertEquals(0.7f, parsed.composite?.photoFraction)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "url=http://insecure.example",
            "verification=verified",
            "lastVerified=\"22-10-2026\"",
            "documents=",
            "unknownKey",
            "schema2",
            "duplicateIds",
        ],
    )
    fun `rejects invalid rule files`(case: String) {
        val text = when (case) {
            "url=http://insecure.example" -> file(profile(url = "http://insecure.example"))
            "verification=verified" -> file(profile(verification = "verified"))
            "lastVerified=\"22-10-2026\"" -> file(profile(lastVerified = "\"22-10-2026\""))
            "documents=" -> file(profile(documents = ""))
            "unknownKey" -> file(profile().dropLast(1) + ""","extra":1}""")
            "schema2" -> file(profile()).replace("\"schemaVersion\":1", "\"schemaVersion\":2")
            "duplicateIds" -> """{"schemaVersion":1,"profiles":[${profile()},${profile()}]}"""
            else -> error(case)
        }
        val result = ExamRulesParser.parse(text)
        assertTrue(result is Result.Failure, "Expected failure for $case")
        assertTrue((result as Result.Failure).error is AppError.Unexpected)
    }
}
