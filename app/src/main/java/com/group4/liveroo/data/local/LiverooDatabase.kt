package com.group4.liveroo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, TaskEntity::class, KnownDeviceEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LiverooDatabase : RoomDatabase() {
    abstract fun groups(): GroupDao
    abstract fun members(): MemberDao
    abstract fun tasks(): TaskDao
    abstract fun devices(): KnownDeviceDao

    companion object {
        fun build(context: Context): LiverooDatabase =
            Room.databaseBuilder(context, LiverooDatabase::class.java, "liveroo.db").build()
    }
}
