package com.group4.liveroo.testsupport

import android.content.Context
import androidx.room.Room
import com.group4.liveroo.data.LiverooRepository
import com.group4.liveroo.data.local.LiverooDatabase
import com.group4.liveroo.sync.SyncManager

/** One simulated phone: its own in-memory database, identity, repository and sync manager. */
class TestPhone(context: Context, val userId: String, val name: String) {
    val identity = FakeIdentityStore(userId, name)
    val db: LiverooDatabase = Room.inMemoryDatabaseBuilder(context, LiverooDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val repo = LiverooRepository(db, identity)
    val sync = SyncManager(repo, identity)

    fun close() {
        db.close()
    }
}
