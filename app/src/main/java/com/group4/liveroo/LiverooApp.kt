package com.group4.liveroo

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.group4.liveroo.bluetooth.BluetoothController
import com.group4.liveroo.data.LiverooRepository
import com.group4.liveroo.data.local.LiverooDatabase
import com.group4.liveroo.data.prefs.DataStoreIdentityStore
import com.group4.liveroo.data.prefs.IdentityStore
import com.group4.liveroo.sync.SyncManager
import com.group4.liveroo.sync.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class LiverooApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container. Swap for Hilt later if you like. */
class AppContainer(context: Context) {
    val database = LiverooDatabase.build(context)
    val identity: IdentityStore = DataStoreIdentityStore(context)
    val repository = LiverooRepository(database, identity)
    val bluetooth = BluetoothController(context)
    val sync = SyncManager(repository, identity)

    /** Bluetooth sessions run here so they survive screen changes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The last sync outcome, shown on the Sync result screen (S16). */
    val lastSyncResult = MutableStateFlow<SyncOutcome?>(null)
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
