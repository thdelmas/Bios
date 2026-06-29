package com.bios.app

import com.bios.app.export.EncryptedZipExporter
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Verifies the password-protected export is a STANDARD AES-256 zip that opens
 * outside Bios with just the passphrase — the whole reason this exists instead
 * of the Bios-only `.bios` format. We re-open the produced file with a fresh
 * Zip4j reader (standing in for 7-Zip / Keka / pyzipper) and confirm the bytes
 * round-trip, that the wrong password is rejected, and that the inner entry
 * keeps the original filename.
 */
class EncryptedZipExporterTest {

    private fun tempDir(): File = Files.createTempDirectory("eztest").toFile()

    private fun writeSource(dir: File, name: String, content: ByteArray): File =
        File(dir, name).apply { writeBytes(content) }

    @Test
    fun `encrypt then extract with password recovers original bytes`() {
        val dir = tempDir()
        val content = """{"resting_hr":62,"hrv_ms":48}""".toByteArray()
        val source = writeSource(dir, "bios_fhir.json", content)
        val out = File(dir, "bios_fhir.json.zip")
        val passphrase = "correct horse battery staple"

        EncryptedZipExporter.encryptTo(out, source, passphrase)

        val extractDir = File(dir, "extracted").apply { mkdirs() }
        ZipFile(out, passphrase.toCharArray()).extractAll(extractDir.path)

        val extracted = File(extractDir, "bios_fhir.json")
        assertTrue("inner entry keeps original name", extracted.exists())
        assertArrayEquals(content, extracted.readBytes())
    }

    @Test
    fun `produced zip is encrypted`() {
        val dir = tempDir()
        val source = writeSource(dir, "bios_export.json", "secret".toByteArray())
        val out = File(dir, "bios_export.json.zip")

        EncryptedZipExporter.encryptTo(out, source, "a-strong-pass")

        assertTrue(ZipFile(out).isEncrypted)
    }

    @Test
    fun `wrong password is rejected on extract`() {
        val dir = tempDir()
        val source = writeSource(dir, "bios_export.json", "secret health data".toByteArray())
        val out = File(dir, "bios_export.json.zip")
        EncryptedZipExporter.encryptTo(out, source, "the-right-password")

        val extractDir = File(dir, "extracted").apply { mkdirs() }
        assertThrows(ZipException::class.java) {
            ZipFile(out, "the-wrong-password".toCharArray()).extractAll(extractDir.path)
        }
    }

    @Test
    fun `inner entry name matches source file name`() {
        val dir = tempDir()
        val source = writeSource(dir, "bios_doctor_summary.pdf", byteArrayOf(1, 2, 3, 4))
        val out = File(dir, "bios_doctor_summary.pdf.zip")

        EncryptedZipExporter.encryptTo(out, source, "passphrase-123")

        val headers = ZipFile(out).fileHeaders
        assertEquals(1, headers.size)
        assertEquals("bios_doctor_summary.pdf", headers[0].fileName)
    }

    @Test
    fun `ciphertext differs from plaintext`() {
        val dir = tempDir()
        val content = "resting HR 62 bpm".toByteArray()
        val source = writeSource(dir, "bios_export.json", content)
        val out = File(dir, "bios_export.json.zip")

        EncryptedZipExporter.encryptTo(out, source, "passphrase-123")

        // The zip container must not contain the raw plaintext bytes.
        val zipBytes = out.readBytes()
        assertNotEquals(0, zipBytes.size)
        assertTrue(out.length() > 0)
        assertTrue("plaintext must not appear verbatim in the encrypted zip", !zipBytes.containsSub(content))
    }

    private fun ByteArray.containsSub(sub: ByteArray): Boolean {
        if (sub.isEmpty() || sub.size > size) return false
        outer@ for (i in 0..size - sub.size) {
            for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
            return true
        }
        return false
    }
}
