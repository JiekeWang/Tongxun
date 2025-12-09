package com.tongxun.data.local.dao

import androidx.room.*
import com.tongxun.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE phoneNumber = :phoneNumber")
    suspend fun getUserByPhone(phoneNumber: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

