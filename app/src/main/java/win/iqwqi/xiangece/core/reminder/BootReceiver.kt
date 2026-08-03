package win.iqwqi.xiangece.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import win.iqwqi.xiangece.data.local.CampusDao

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var dao: CampusDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.restore(dao.upcomingReminders())
            } finally {
                pending.finish()
            }
        }
    }
}
