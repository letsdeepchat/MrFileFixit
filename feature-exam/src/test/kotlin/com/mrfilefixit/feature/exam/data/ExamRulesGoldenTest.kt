package com.mrfilefixit.feature.exam.data

import com.mrfilefixit.core.domain.exam.ExamProfile
import com.mrfilefixit.core.domain.exam.VerificationStatus
import com.mrfilefixit.core.domain.result.Result
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden test for the bundled exam rules. Any change to a number in exam_rules.json shows up as a
 * readable diff here, so rule edits are reviewed deliberately.
 *
 * To accept an intended change: `./gradlew :feature-exam:testDebugUnitTest -Pgolden.update=true`.
 */
class ExamRulesGoldenTest {
    private val moduleDir = File(System.getProperty("mff.moduleDir") ?: ".")
    private val rulesFile = moduleDir.resolve("src/main/assets/exam_rules.json")
    private val goldenFile = moduleDir.resolve("src/test/resources/golden/exam_rules.golden.txt")

    private fun parseBundled(): List<ExamProfile> {
        val result = ExamRulesParser.parse(rulesFile.readText())
        assertTrue(result is Result.Success, "Bundled rules failed to parse: $result")
        return (result as Result.Success).value
    }

    @Test
    fun `bundled rules match golden snapshot`() {
        val rendered = render(parseBundled())
        if (System.getProperty("golden.update") == "true") {
            goldenFile.parentFile?.mkdirs()
            goldenFile.writeText(rendered)
        }
        assertEquals(goldenFile.readText(), rendered)
    }

    @Test
    fun `every bundled document can be generated and passes its own checker`() {
        for (profile in parseBundled()) {
            for (doc in profile.documents) {
                val target = doc.targetBytes
                assertTrue(target.first in doc.minBytes..doc.maxBytes, "${profile.id}/${doc.kind} min")
                assertTrue(target.last in doc.minBytes..doc.maxBytes, "${profile.id}/${doc.kind} max")
                assertTrue(doc.targetWidth in doc.width && doc.targetHeight in doc.height)
            }
        }
    }

    @Test
    fun `catalog covers at least nineteen exams with unique short names`() {
        val profiles = parseBundled()
        assertTrue(profiles.size >= 19, "Only ${profiles.size} exams bundled")
        assertEquals(profiles.size, profiles.map { it.shortName }.toSet().size)
    }

    @Test
    fun `bundled profiles are labelled honestly`() {
        for (profile in parseBundled()) {
            assertTrue(profile.sourceUrl.startsWith("https://"))
            if (profile.verification == VerificationStatus.VERIFIED) {
                assertTrue(profile.lastVerified != null, "${profile.id} is verified without a date")
            }
        }
    }

    private fun render(profiles: List<ExamProfile>): String = buildString {
        for (p in profiles) {
            appendLine("${p.id} | ${p.shortName} | ${p.category} | ${p.verification} | lastVerified=${p.lastVerified}")
            appendLine("  source: ${p.sourceUrl}")
            for (d in p.documents) {
                appendLine(
                    "  ${d.kind}: ${d.formats.sorted().joinToString("/")} " +
                        "${d.minKb}-${d.maxKb}KB(x${d.kilobyteBytes}) " +
                        "w=${d.width.min}..${d.width.max} h=${d.height.min}..${d.height.max} " +
                        "target=${d.targetWidth}x${d.targetHeight} bytes=${d.targetBytes}",
                )
            }
            for (c in p.certificates) {
                appendLine("  certificate: ${c.name}: ${c.formats.sorted().joinToString("/")} ${c.minKb}-${c.maxKb}KB")
            }
            p.composite?.let { c ->
                val size = "${c.document.targetWidth}x${c.document.targetHeight}"
                appendLine("  composite: photoFraction=${c.photoFraction} target=$size")
            }
        }
    }
}
