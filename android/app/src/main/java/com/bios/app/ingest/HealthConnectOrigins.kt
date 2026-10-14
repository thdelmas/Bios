package com.bios.app.ingest

import com.bios.app.data.dao.DataSourceDao
import com.bios.app.model.DataSource
import com.bios.app.model.SensorType
import com.bios.app.model.SourceType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One Bios source per app that writes into Health Connect
 * (docs/specs/source-preference.md, criterion 1). A Health Connect record's
 * witness is the app named in its data origin: COROS, Fitbit and the
 * handset's own pedometer are three sources, not one "Health Connect".
 * `deviceModel` holds the package name, the stable key; `deviceName` is the
 * app label the owner reads ("COROS via Health Connect").
 */
internal class HealthConnectOrigins(
    private val sourceDao: DataSourceDao,
    private val appLabel: (String) -> String,
) {
    private val cache = mutableMapOf<String, String>()
    private val lock = Mutex()

    /** Source id for [packageName], registering it on first sight. */
    suspend fun sourceFor(packageName: String): String = lock.withLock {
        cache[packageName] ?: register(packageName).also { cache[packageName] = it }
    }

    private suspend fun register(packageName: String): String {
        val name = "${appLabel(packageName)} via Health Connect"
        sourceDao.getAll()
            .firstOrNull { it.sourceType == SourceType.HEALTH_CONNECT.key && it.deviceModel == packageName }
            ?.let { existing ->
                // Labels improve over builds; keep the row, refresh the name.
                if (existing.deviceName != name) sourceDao.insert(existing.copy(deviceName = name))
                return existing.id
            }
        val source = DataSource(
            sourceType = SourceType.HEALTH_CONNECT.key,
            deviceName = name,
            deviceModel = packageName,
            sensorType = SensorType.OPTICAL_HR.name,
        )
        sourceDao.insert(source)
        return source.id
    }
}
