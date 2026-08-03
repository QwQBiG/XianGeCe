package win.iqwqi.xiangece.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 记账记录。type: 0=支出, 1=收入。 */
@Entity(
    tableName = "expense_records",
    indices = [Index(value = ["occurredAtEpochMillis"], name = "index_expense_records_occurredAtEpochMillis")],
)
data class ExpenseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 金额（分），避免浮点误差 */
    val amountCents: Long,
    /** 0=支出, 1=收入 */
    val type: Int,
    /** 分类 key，见 ExpenseCategory */
    val category: String,
    /** 备注 */
    val note: String,
    /** 发生时间（epoch millis） */
    val occurredAtEpochMillis: Long,
    /** 创建时间（epoch millis） */
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
)
