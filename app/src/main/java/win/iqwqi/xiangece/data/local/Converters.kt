package win.iqwqi.xiangece.data.local

import androidx.room.TypeConverter
import win.iqwqi.xiangece.domain.model.HabitFrequency
import win.iqwqi.xiangece.domain.model.InboxStatus
import win.iqwqi.xiangece.domain.model.TaskStatus
import win.iqwqi.xiangece.domain.model.WeekParity

class Converters {
    @TypeConverter fun fromWeekParity(value: WeekParity): String = value.name
    @TypeConverter fun toWeekParity(value: String): WeekParity = WeekParity.valueOf(value)
    @TypeConverter fun fromTaskStatus(value: TaskStatus): String = value.name
    @TypeConverter fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
    @TypeConverter fun fromInboxStatus(value: InboxStatus): String = value.name
    @TypeConverter fun toInboxStatus(value: String): InboxStatus = InboxStatus.valueOf(value)
    @TypeConverter fun fromHabitFrequency(value: HabitFrequency): String = value.name
    @TypeConverter fun toHabitFrequency(value: String): HabitFrequency = HabitFrequency.valueOf(value)
}

