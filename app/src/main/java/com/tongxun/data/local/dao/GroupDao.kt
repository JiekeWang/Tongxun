package com.tongxun.data.local.dao

import androidx.room.*
import com.tongxun.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    
    @Query("SELECT * FROM groups")
    fun getAllGroups(): Flow<List<GroupEntity>>
    
    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?
    
    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    fun getGroupByIdFlow(groupId: String): Flow<GroupEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)
    
    @Update
    suspend fun updateGroup(group: GroupEntity)
    
    @Delete
    suspend fun deleteGroup(group: GroupEntity)
    
    @Query("DELETE FROM groups WHERE groupId = :groupId")
    suspend fun deleteGroupById(groupId: String)
    
    @Query("DELETE FROM groups")
    suspend fun deleteAllGroups()
}

