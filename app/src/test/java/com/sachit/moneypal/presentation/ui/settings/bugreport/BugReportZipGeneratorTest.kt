package com.sachit.moneypal.presentation.ui.settings.bugreport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry

class BugReportZipGeneratorTest {

    private val safeName = Regex("^[A-Za-z0-9._-]*$")

    private fun assertSafeEntry(name: String) {
        val result = BugReportZipGenerator.sanitizeZipEntryName(name)
        assertTrue(
            "sanitized '$name' -> '$result' must match $safeName",
            safeName.matches(result),
        )
        assertTrue("sanitized '$name' -> '$result' must contain no '/'", !result.contains('/'))
        assertTrue("sanitized '$name' -> '$result' must contain no '\\'", !result.contains('\\'))
        assertTrue(
            "sanitized '$name' -> '$result' must contain no '..'",
            !result.contains(".."),
        )
        val entryName = ZipEntry("attachments/$result").name
        assertTrue("zip entry '$entryName' must not traverse", !entryName.contains(".."))
    }

    @Test
    fun `plain name is left unchanged`() {
        assertEquals("vacation.png", BugReportZipGenerator.sanitizeZipEntryName("vacation.png"))
    }

    @Test
    fun `path traversal is collapsed to a single segment`() {
        val result = BugReportZipGenerator.sanitizeZipEntryName("../../evil.png")
        assertTrue("no '/' expected in '$result'", !result.contains('/'))
        assertTrue("no '\\\\' expected in '$result'", !result.contains('\\'))
        assertTrue("no '..' expected in '$result'", !result.contains(".."))
    }

    @Test
    fun `bare traversal names sanitize to blank`() {
        assertEquals("", BugReportZipGenerator.sanitizeZipEntryName(".."))
        assertEquals("", BugReportZipGenerator.sanitizeZipEntryName("..."))
        assertEquals("", BugReportZipGenerator.sanitizeZipEntryName("__"))
        assertEquals("", BugReportZipGenerator.sanitizeZipEntryName("///"))
    }

    @Test
    fun `backslash separators never survive`() {
        val result = BugReportZipGenerator.sanitizeZipEntryName("a\\b.png")
        assertTrue("no backslash expected in '$result'", !result.contains('\\'))
        assertTrue("no slash expected in '$result'", !result.contains('/'))
    }

    @Test
    fun `accented characters are stripped via NFD`() {
        assertEquals("cafe.png", BugReportZipGenerator.sanitizeZipEntryName("café.png"))
    }

    @Test
    fun `spaces and punctuation become underscores`() {
        val result = BugReportZipGenerator.sanitizeZipEntryName("my file (1).png")
        assertTrue(safeName.matches(result))
        assertTrue("expected a readable slug in '$result'", result.contains("my_file"))
        assertTrue("expected the digit to survive in '$result'", result.contains("1"))
        assertTrue("expected the extension dot to survive in '$result'", result.endsWith(".png"))
    }

    @Test
    fun `very long names stay bounded and regex-clean`() {
        val longName = "a".repeat(200) + ".png"
        val result = BugReportZipGenerator.sanitizeZipEntryName(longName)
        assertTrue("expected length <= 204 but was ${result.length}", result.length <= 204)
        assertTrue(safeName.matches(result))
    }

    @Test
    fun `every hostile name yields a traversal-free zip entry`() {
        val cases = listOf(
            "vacation.png",
            "../../evil.png",
            "..\\..\\secret.txt",
            "a\\b.png",
            "café.png",
            "my file (1).png",
            "..",
            "...",
            "C:\\Users\\Public\\..\\..\\x",
            "https://evil.example/../../x",
            "screenshot_2026-09-04.png",
        )
        cases.forEach { assertSafeEntry(it) }
    }
}
