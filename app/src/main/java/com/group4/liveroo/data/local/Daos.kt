package com.group4.liveroo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observe(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM groups")
    suspend fun getAll(): List<GroupEntity>

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE groups SET dirty = 0 WHERE id IN (:ids) AND updatedAt <= :upTo")
    suspend fun markClean(ids: List<String>, upTo: Long)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId AND leftAt IS NULL ORDER BY displayName")
    fun observeActive(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members")
    fun observeAllMembers(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getAll(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM members WHERE groupId = :groupId AND userId = :userId")
    suspend fun get(groupId: String, userId: String): MemberEntity?

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query("UPDATE members SET dirty = 0 WHERE groupId IN (:ids) AND updatedAt <= :upTo")
    suspend fun markClean(ids: List<String>, upTo: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE groupId = :groupId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeForGroup(groupId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE assigneeId = :userId AND deleted = 0 ORDER BY updatedAt DESC")
    fun observeAssignedTo(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE groupId = :groupId")
    suspend fun getAllForGroup(groupId: String): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query("UPDATE tasks SET dirty = 0 WHERE groupId IN (:ids) AND updatedAt <= :upTo")
    suspend fun markClean(ids: List<String>, upTo: Long)

    /** Number of changes not yet synced, for the Sync tab badge. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM tasks WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM groups WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM members WHERE dirty = 1)"
    )
    fun observeUnsyncedCount(): Flow<Int>
}

@Dao
interface KnownDeviceDao {
    @Query("SELECT * FROM known_devices")
    fun observeAll(): Flow<List<KnownDeviceEntity>>

    @Upsert
    suspend fun upsert(device: KnownDeviceEntity)
}
