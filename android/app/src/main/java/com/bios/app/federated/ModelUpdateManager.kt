package com.bios.app.federated

import android.content.Context
import android.util.Base64
import android.util.Log
import com.bios.app.platform.IpfsClient
import com.bios.app.platform.PlatformDetector
import kotlinx.coroutines.runBlocking
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.File

/**
 * Manages TFLite model updates via OTA.
 *
 * On LETHE: models arrive via IPFS-verified update channel (signature-checked)
 * On stock Android: models bundled in app updates, or downloaded in-app with
 * Ed25519 signature verification.
 *
 * The owner can opt out of model updates. Opting out keeps the bundled model.
 */
class ModelUpdateManager(private val context: Context) {

    private val modelsDir = File(context.filesDir, "models")

    init {
        modelsDir.mkdirs()
    }

    /**
     * Check if a newer model is available locally (downloaded or received via OTA).
     * Returns the model file if available, null if bundled model is current.
     */
    fun getLatestModel(): File? {
        val modelFile = File(modelsDir, MODEL_FILENAME)
        if (!modelFile.exists()) return null

        val versionFile = File(modelsDir, VERSION_FILENAME)
        if (!versionFile.exists()) return null

        val localVersion = versionFile.readText().trim().toIntOrNull() ?: return null
        if (localVersion <= BUNDLED_MODEL_VERSION) return null

        return modelFile
    }

    /**
     * Install a new model from bytes (after signature verification).
     */
    fun installModel(modelBytes: ByteArray, version: Int, signature: ByteArray): Boolean {
        if (!verifySignature(modelBytes, signature)) {
            Log.w(TAG, "Model signature verification failed — rejecting update")
            return false
        }

        if (version <= currentVersion()) {
            Log.d(TAG, "Model version $version is not newer than current ${currentVersion()}")
            return false
        }

        val modelFile = File(modelsDir, MODEL_FILENAME)
        val versionFile = File(modelsDir, VERSION_FILENAME)

        modelFile.writeBytes(modelBytes)
        versionFile.writeText(version.toString())

        Log.i(TAG, "Installed model version $version (${modelBytes.size} bytes)")
        return true
    }

    /**
     * Check for model updates via IPFS/IPNS.
     *
     * On LETHE: resolves the `bios-models` IPNS name to find the latest
     * model manifest, downloads the model by CID, verifies Ed25519 signature.
     * All traffic Tor-routed by the IPFS daemon.
     *
     * @return true if a new model was installed
     */
    fun checkForUpdate(): Boolean {
        val ipfs = IpfsClient()
        return runBlocking {
            if (!ipfs.isAvailable()) return@runBlocking false

            try {
                val cid = ipfs.nameResolve(IPNS_CHANNEL) ?: return@runBlocking false
                val manifestBytes = ipfs.cat(cid) ?: return@runBlocking false
                val manifest = JSONObject(String(manifestBytes, Charsets.UTF_8))

                val version = manifest.getInt("version")
                if (version <= currentVersion()) return@runBlocking false

                val modelCid = manifest.getString("model_cid")
                val sigB64 = manifest.getString("signature")

                val modelBytes = ipfs.cat(modelCid) ?: return@runBlocking false
                val signature = Base64.decode(sigB64, Base64.NO_WRAP)

                installModel(modelBytes, version, signature)
            } catch (e: Exception) {
                Log.w(TAG, "IPNS model check failed: ${e.message}")
                false
            }
        }
    }

    fun currentVersion(): Int {
        val versionFile = File(modelsDir, VERSION_FILENAME)
        if (!versionFile.exists()) return BUNDLED_MODEL_VERSION
        return versionFile.readText().trim().toIntOrNull() ?: BUNDLED_MODEL_VERSION
    }

    /**
     * Verify Ed25519 signature of a model file.
     * The signing public key is bundled in the app.
     */
    private fun verifySignature(data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val pubKeyBytes = Base64.decode(SIGNING_PUBLIC_KEY_B64, Base64.NO_WRAP)
            val pubKey = Ed25519PublicKeyParameters(pubKeyBytes, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pubKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error", e)
            false
        }
    }

    companion object {
        private const val TAG = "BiosModelUpdate"
        const val MODEL_FILENAME = "anomaly_detector.tflite"
        const val VERSION_FILENAME = "model_version.txt"
        const val BUNDLED_MODEL_VERSION = 1

        // IPNS channel for model updates (resolved via local IPFS daemon)
        const val IPNS_CHANNEL = "bios-models"

        // Ed25519 public key for model signature verification.
        // Private key stored offline — never committed to source control.
        // Replace with production key before release.
        private const val SIGNING_PUBLIC_KEY_B64 = "a19PcWyAr5fWucpCaBLCQGpAlWKOmnAqOhPxcTHzbD4="
    }
}
