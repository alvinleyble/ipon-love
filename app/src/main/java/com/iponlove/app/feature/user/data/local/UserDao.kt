package com.iponlove.app.feature.user.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Upsert
    suspend fun upsert(entity: UserEntity)

    @Upsert
    suspend fun upsertAll(entities: List<UserEntity>)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<UserEntity>

    @Query("UPDATE users SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)
}
