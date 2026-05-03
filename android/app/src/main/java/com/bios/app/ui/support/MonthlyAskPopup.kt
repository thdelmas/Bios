package com.bios.app.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bios.app.R

/**
 * Monthly community ask popup. Three honest paths + dismiss. The app does
 * not track which path the user chose, does not differentiate between users,
 * and does not communicate with the donation platform. Donation is a pure
 * browser hand-off.
 *
 * See /home/mia/autonomo/android-apps-contribution-popup-guide.md.
 */
private const val DONATION_URL = "https://theophile.world/sponsor"
private const val FEEDBACK_EMAIL = "contact@theophile.world"

@Composable
fun MonthlyAskPopup(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.monthly_ask_title)) },
        text = { Text(stringResource(R.string.monthly_ask_body)) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    openUrl(context, DONATION_URL)
                    onClose()
                }) { Text(stringResource(R.string.monthly_ask_donate)) }
                TextButton(onClick = {
                    openPlayStoreReview(context)
                    onClose()
                }) { Text(stringResource(R.string.monthly_ask_review)) }
                TextButton(onClick = {
                    openFeedbackEmail(
                        context,
                        FEEDBACK_EMAIL,
                        context.getString(R.string.monthly_ask_feedback_subject)
                    )
                    onClose()
                }) { Text(stringResource(R.string.monthly_ask_feedback)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.monthly_ask_dismiss))
            }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openPlayStoreReview(context: Context) {
    val pkg = context.packageName.removeSuffix(".lethe")
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(market) }.isSuccess
    if (!opened) {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }
}

private fun openFeedbackEmail(context: Context, email: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$email?subject=" + Uri.encode(subject))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
