package com.bios.app.privacy

import android.content.Context
import androidx.work.*
import com.bios.app.data.BiosDatabase
import com.bios.app.model.PrivacyTier
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically generates and transmits
 * anonymous community contributions for Community tier users.
 *
 * Contribution frequency is randomized to prevent timing-based deanonymization.
 */
class ContributionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        val tier = prefs.getString("privacy_tier", PrivacyTier.PRIVATE.name)

        // Only contribute if user has opted into Community tier
        if (tier != PrivacyTier.COMMUNITY.name) return Result.success()

        return try {
            val db = BiosDatabase.getInstance(applicationContext)
            val aggregator = CommunityAggregator(db)

            val ageBracket = prefs.getString("age_bracket", null)
            val contribution = aggregator.generateContribution(
                userAgeBracket = ageBracket,
                deviceClass = "wrist_wearable"
            )

            if (contribution != null) {
                // TODO: Transmit to server via TLS 1.3
                // The contribution contains NO identifiers, NO raw data, NO timestamps.
                // CommunityApiClient.submit(contribution)
            }

            // Schedule next contribution at a random delay
            val nextDelay = aggregator.nextContributionDelayMillis()
            enqueueNextContribution(applicationContext, nextDelay)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "bios_community_contribution"

        fun enqueueNextContribution(context: Context, delayMillis: Long? = null) {
            val delay = delayMillis ?: (18 * 3600 * 1000L) // default 18h

            val request = OneTimeWorkRequestBuilder<ContributionWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
