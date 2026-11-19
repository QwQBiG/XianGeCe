package win.iqwqi.xiangece.feature.notebook

import android.app.Activity
import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import win.iqwqi.xiangece.data.settings.AppSettingsStore
import win.iqwqi.xiangece.feature.notebook.ui.NotebookActivity
import win.iqwqi.xiangece.feature.diting.ui.DitingActivity

@RunWith(AndroidJUnit4::class)
class NotebookAppearanceTest {
    @Test fun firstDrawUsesAppDarkModeBeforeAnyFrame() = assertFirstDarkFrame(NotebookActivity::class.java)
    @Test fun classroomFirstDrawAlsoUsesAppDarkMode() = assertFirstDarkFrame(DitingActivity::class.java)

    private fun assertFirstDarkFrame(target: Class<out Activity>) = runBlocking {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val settings = AppSettingsStore(application)
        val before = settings.settings.first()
        val drawn = CountDownLatch(1)
        val firstColor = AtomicInteger(android.graphics.Color.WHITE)
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (!target.isInstance(activity)) return
                val view = activity.window.decorView
                view.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        if (drawn.count > 0) {
                            firstColor.set((view.background as? ColorDrawable)?.color ?: android.graphics.Color.WHITE)
                            drawn.countDown()
                        }
                        view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
                    }
                })
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        try {
            settings.updateGeneral(onboardingComplete = before.onboardingComplete, darkMode = true,
                followSystemTheme = false, courseReminderMinutes = before.courseReminderMinutes)
            application.registerActivityLifecycleCallbacks(callback)
            ActivityScenario.launch(target).use {
                assertTrue("First frame was not drawn", drawn.await(20, TimeUnit.SECONDS))
                assertTrue("First frame window was light", androidx.core.graphics.ColorUtils.calculateLuminance(firstColor.get()) < 0.2)
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callback)
            settings.updateGeneral(onboardingComplete = before.onboardingComplete, darkMode = before.darkMode,
                followSystemTheme = before.followSystemTheme, courseReminderMinutes = before.courseReminderMinutes)
        }
    }
}
