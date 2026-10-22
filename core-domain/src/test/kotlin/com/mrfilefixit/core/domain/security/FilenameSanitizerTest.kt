package com.mrfilefixit.core.domain.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FilenameSanitizerTest {
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(
        "../../etc/passwd, etc_passwd",
        "photo.jpg, photo.jpg",
        "my/sub\\dir/file.pdf, my_sub_dir_file.pdf",
        "'  ..hidden  ', hidden",
        "a:b*c?d<e>f|g, a_b_c_d_e_f_g",
        "CON.txt, _CON.txt",
        "'', file",
        "///, file",
        "हस्ताक्षर.jpg, हस्ताक्षर.jpg",
    )
    fun `sanitizes names`(input: String, expected: String) {
        assertEquals(expected, FilenameSanitizer.sanitize(input))
    }

    @Test
    fun `removes control and bidi override characters`() {
        val evil = "invoice\u0000‮gpj.exe\n"
        val result = FilenameSanitizer.sanitize(evil)
        assertFalse(result.any { it.isISOControl() || it == '‮' })
        assertEquals("invoicegpj.exe", result)
    }

    @Test
    fun `truncates long names but keeps the extension`() {
        val result = FilenameSanitizer.sanitize("a".repeat(500) + ".pdf")
        assertEquals(FilenameSanitizer.MAX_LENGTH, result.length)
        assertTrue(result.endsWith(".pdf"))
    }

    @Test
    fun `withExtension replaces extension and cleans it`() {
        assertEquals("scan.pdf", FilenameSanitizer.withExtension("scan.jpg", ".PDF"))
        assertEquals("file.jpg", FilenameSanitizer.withExtension("../", "jpg"))
        assertTrue(FilenameSanitizer.withExtension("x".repeat(300), "jpeg").length <= FilenameSanitizer.MAX_LENGTH)
    }
}
