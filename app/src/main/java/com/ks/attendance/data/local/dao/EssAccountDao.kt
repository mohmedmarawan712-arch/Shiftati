package com.ks.attendance.data.local.dao

import androidx.room.*
import com.ks.attendance.data.local.entity.EssAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface EssAccountDao {

    @Query("SELECT * FROM ess_accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<EssAccount>>

    @Query("SELECT * FROM ess_accounts ORDER BY id ASC")
    suspend fun getAllAccountsDirect(): List<EssAccount>

    @Query("SELECT * FROM ess_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveAccount(): Flow<EssAccount?>

    @Query("SELECT * FROM ess_accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveAccountDirect(): EssAccount?

    @Query("SELECT * FROM ess_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): EssAccount?

    @Query("SELECT * FROM ess_accounts WHERE email = :email LIMIT 1")
    suspend fun getAccountByEmail(email: String): EssAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: EssAccount): Long

    @Update
    suspend fun update(account: EssAccount)

    @Delete
    suspend fun delete(account: EssAccount)

    @Query("DELETE FROM ess_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE ess_accounts SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE ess_accounts SET isActive = (CASE WHEN id = :id THEN 1 ELSE 0 END)")
    suspend fun setActiveFlag(id: Long)

    @Transaction
    suspend fun setActiveAccount(id: Long) {
        clearActiveFlag()
        setActiveFlag(id)
    }
}
