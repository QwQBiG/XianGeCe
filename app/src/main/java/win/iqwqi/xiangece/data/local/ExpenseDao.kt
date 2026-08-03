package win.iqwqi.xiangece.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    /** 查询指定时间范围内的所有记录，按时间倒序。 */
    @Query("SELECT * FROM expense_records WHERE occurredAtEpochMillis BETWEEN :startMillis AND :endMillis ORDER BY occurredAtEpochMillis DESC")
    fun observeRange(startMillis: Long, endMillis: Long): Flow<List<ExpenseRecordEntity>>

    /** 查询全部记录（用于统计/导出）。 */
    @Query("SELECT * FROM expense_records ORDER BY occurredAtEpochMillis DESC")
    fun observeAll(): Flow<List<ExpenseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExpenseRecordEntity): Long

    @Query("DELETE FROM expense_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expense_records")
    suspend fun deleteAll()
}
