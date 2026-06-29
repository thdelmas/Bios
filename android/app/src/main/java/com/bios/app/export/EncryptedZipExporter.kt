package com.bios.app.export

import android.content.Context
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

/**
 * Wraps an already-produced export file in a password-protected, AES-256
 * encrypted ZIP.
 *
 * Unlike [EncryptedExporter]'s Bios-only `.bios` container, a standard AES zip
 * opens **outside** Bios with just the password — 7-Zip, Keka, the macOS/Windows
 * archive tools (with an AES-capable helper), Python's `pyzipper` — so a
 * clinician, the owner, or another agent can read the data given the passphrase.
 * That interoperability is the whole point: the encryption protects the file in
 * transit/at rest without locking the owner into Bios to read it back.
 *
 * Zip4j is pure-Java (no Play/native deps), so this works on degoogled builds.
 */
object EncryptedZipExporter {

    /**
     * Encrypt [source] into `"<source-name>.zip"` in the cache dir, protected by
     * [passphrase] using AES-256. The entry inside keeps [source]'s filename, so
     * the recipient sees e.g. `bios_fhir.json` after extracting.
     *
     * The caller owns [source]'s lifecycle (this does not delete it) and should
     * remove the plaintext copy once the encrypted zip is delivered.
     */
    fun encrypt(context: Context, source: File, passphrase: String): File =
        encryptTo(File(context.cacheDir, source.name + ".zip"), source, passphrase)

    /**
     * Context-free core: encrypt [source] into [outFile]. Split out from
     * [encrypt] so the crypto/format can be unit-tested without an Android
     * Context (the only thing [encrypt] needs a Context for is the cache dir).
     */
    internal fun encryptTo(outFile: File, source: File, passphrase: String): File {
        if (outFile.exists()) outFile.delete()

        val params = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            compressionLevel = CompressionLevel.NORMAL
            fileNameInZip = source.name
        }

        ZipFile(outFile, passphrase.toCharArray()).use { zip ->
            zip.addFile(source, params)
        }
        return outFile
    }
}
