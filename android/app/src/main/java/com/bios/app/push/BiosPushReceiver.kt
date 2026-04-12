package com.bios.app.push

import android.util.Log
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * UnifiedPush service registered in AndroidManifest.
 *
 * The distributor app (ntfy, NextPush, etc.) delivers push events here
 * via the connector library's internal BroadcastReceiver.
 * All logic is delegated to [PushRegistrationManager] and [PushMessageHandler].
 */
class BiosPushService : PushService() {

    companion object {
        private const val TAG = "BiosPushService"
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New push endpoint received")
        PushRegistrationManager.onEndpointReceived(this, endpoint.url)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "Push registration failed: $reason")
        PushRegistrationManager.onRegistrationFailed(this)
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "Push unregistered by distributor")
        PushRegistrationManager.onRegistrationFailed(this)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        Log.d(TAG, "Push message received (${message.content.size} bytes)")
        PushMessageHandler(this).handle(message.content)
    }
}
