package com.tongxun.data.local.dao

import androidx.room.*
import com.tongxun.data.local.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    
    @Query("SELECT * FROM friends WHERE userId = :userId AND isBlocked = 0 ORDER BY createdAt DESC")
    fun getFriends(userId: String): Flow<List<FriendEntity>>
    
    @Query("SELECT * FROM friends WHERE userId = :userId AND friendId = :friendId")
    suspend fun getFriend(userId: String, friendId: String): FriendEntity?
    
    @Query("SELECT * FROM friends WHERE userId = :userId AND friendId = :friendId")
    fun getFriendFlow(userId: String, friendId: String): Flow<FriendEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<FriendEntity>)
    
    @Update
    suspend fun updateFriend(friend: FriendEntity)
    
    @Delete
    suspend fun deleteFriend(friend: FriendEntity)
    
    @Query("DELETE FROM friends WHERE userId = :userId AND friendId = :friendId")
    suspend fun deleteFriend(userId: String, friendId: String)
    
    @Query("UPDATE friends SET isBlocked = :isBlocked WHERE userId = :userId AND friendId = :friendId")
    suspend fun updateBlockStatus(userId: String, friendId: String, isBlocked: Boolean)
    
    @Query("DELETE FROM friends WHERE userId = :userId")
    suspend fun deleteAllFriends(userId: String)
    
    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()
}

