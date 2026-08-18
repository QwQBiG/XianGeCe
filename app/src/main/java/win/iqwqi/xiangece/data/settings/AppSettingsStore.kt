package win.iqwqi.xiangece.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.settingsDataStore by preferencesDataStore("xiangece_settings")

/** 当前设置 schema 版本。每次做不向后兼容的改动（改名/删字段/改默认值）时 +1 并在 [MIGRATIONS] 添加迁移。 */
private const val SETTINGS_SCHEMA_VERSION = 1

@Serializable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val darkMode: Boolean = false,
    val followSystemTheme: Boolean = false,
    val themeSeed: String = "pine",
    val courseReminderMinutes: Int = 30,
    val taskReminderHoursFirst: Int = 24,
    val taskReminderHoursSecond: Int = 2,
    val notificationPermissionAsked: Boolean = false,
    val gradeScheme: String = "4.0",
    val customGradeRules: String = "90=4.0, 85=3.7, 80=3.3, 70=2.3, 60=1.0, 0=0",
    val aiEnabled: Boolean = false,
    val aiProvider: String = "自定义",
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    val aiVisionModel: String = "",
    val aiAuthHeader: String = "Authorization: Bearer {key}",
    val aiSupportsVision: Boolean = false,
    val encryptedApiKey: String = "",
    val ditingTranscriptionModel: String = "whisper-1",
    val ditingTranscriptionEndpoint: String = "",
    val ditingAiAnnotationEnabled: Boolean = false,
    val timetableColumnWidthDp: Int = 46,
    val timetableRowHeightDp: Int = 74,
    val timetablePeriodCount: Int = 16,
    val timetableWallpaperPath: String = "",
    val timetableWallpaperAlpha: Float = 0.28f,
    val timetableShowEmptyCellsAlways: Boolean = true,
    val accountEmail: String = "",
    val accountPasswordEncrypted: String = "",
    val accountLoggedIn: Boolean = false,
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding")
        val darkMode = booleanPreferencesKey("dark_mode")
        val followSystemTheme = booleanPreferencesKey("follow_system_theme")
        val themeSeed = stringPreferencesKey("theme_seed")
        val courseReminder = intPreferencesKey("course_reminder")
        val taskReminderFirst = intPreferencesKey("task_reminder_first")
        val taskReminderSecond = intPreferencesKey("task_reminder_second")
        val notificationPermissionAsked = booleanPreferencesKey("notification_permission_asked")
        val gradeScheme = stringPreferencesKey("grade_scheme")
        val customGradeRules = stringPreferencesKey("custom_grade_rules")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiModel = stringPreferencesKey("ai_model")
        val aiVisionModel = stringPreferencesKey("ai_vision_model")
        val aiAuthHeader = stringPreferencesKey("ai_auth_header")
        val aiSupportsVision = booleanPreferencesKey("ai_supports_vision")
        val encryptedApiKey = stringPreferencesKey("ai_key")
        val ditingTranscriptionModel = stringPreferencesKey("diting_transcription_model")
        val ditingTranscriptionEndpoint = stringPreferencesKey("diting_transcription_endpoint")
        val ditingAiAnnotationEnabled = booleanPreferencesKey("diting_ai_annotation_enabled")
        val timetableColumnWidth = intPreferencesKey("timetable_column_width")
        val timetableRowHeight = intPreferencesKey("timetable_row_height")
        val timetablePeriodCount = intPreferencesKey("timetable_period_count")
        val timetableWallpaperPath = stringPreferencesKey("timetable_wallpaper_path")
        val timetableWallpaperAlpha = floatPreferencesKey("timetable_wallpaper_alpha")
        val timetableShowEmptyCellsAlways = booleanPreferencesKey("timetable_show_empty_cells_always")
        val accountEmail = stringPreferencesKey("account_email")
        val accountPassword = stringPreferencesKey("account_password")
        val accountLoggedIn = booleanPreferencesKey("account_logged_in")
        val schemaVersion = intPreferencesKey("settings_schema_version")
    }

    /**
     * 设置 schema 迁移注册表。key = 起始版本号，value = 把该版本 prefs 迁移到下一版本的函数。
     * 做不向后兼容改动时：
     *   1. 把 [SETTINGS_SCHEMA_VERSION] +1
     *   2. 在这里添加 `prevVersion to { prefs -> prefs.toMutablePreferences().apply { ... } }`
     * 例：把 taskReminderHoursSecond 默认值从 2 改成 1：
     *   1 to { prefs ->
     *       prefs.toMutablePreferences().apply {
     *           // 仅在用户未自定义时回落到新默认
     *           if (this[Keys.taskReminderSecond] == 2) this[Keys.taskReminderSecond] = 1
     *       }
     *   }
     */
    private val MIGRATIONS: Map<Int, (Preferences) -> Preferences> = emptyMap()

    private suspend fun runMigrationsIfNeeded(current: Preferences): Preferences {
        val storedVersion = current[Keys.schemaVersion] ?: 0
        if (storedVersion >= SETTINGS_SCHEMA_VERSION) return current
        var prefs = current
        for (version in storedVersion until SETTINGS_SCHEMA_VERSION) {
            prefs = MIGRATIONS[version]?.invoke(prefs) ?: prefs
        }
        // 写入新版本号
        context.settingsDataStore.edit { it[Keys.schemaVersion] = SETTINGS_SCHEMA_VERSION }
        return prefs
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val migrated = runMigrationsIfNeeded(prefs)
            AppSettings(
                onboardingComplete = migrated[Keys.onboarding] ?: false,
                darkMode = migrated[Keys.darkMode] ?: false,
                followSystemTheme = migrated[Keys.followSystemTheme] ?: false,
                themeSeed = migrated[Keys.themeSeed] ?: "pine",
                courseReminderMinutes = migrated[Keys.courseReminder] ?: 30,
                taskReminderHoursFirst = migrated[Keys.taskReminderFirst] ?: 24,
                taskReminderHoursSecond = migrated[Keys.taskReminderSecond] ?: 2,
                notificationPermissionAsked = migrated[Keys.notificationPermissionAsked] ?: false,
                gradeScheme = migrated[Keys.gradeScheme] ?: "4.0",
                customGradeRules = migrated[Keys.customGradeRules]
                    ?: "90=4.0, 85=3.7, 80=3.3, 70=2.3, 60=1.0, 0=0",
                aiEnabled = migrated[Keys.aiEnabled] ?: false,
                aiProvider = migrated[Keys.aiProvider] ?: "自定义",
                aiBaseUrl = migrated[Keys.aiBaseUrl].orEmpty(),
                aiModel = migrated[Keys.aiModel].orEmpty(),
                aiVisionModel = migrated[Keys.aiVisionModel].orEmpty(),
                aiAuthHeader = migrated[Keys.aiAuthHeader] ?: "Authorization: Bearer {key}",
                aiSupportsVision = migrated[Keys.aiSupportsVision] ?: false,
                encryptedApiKey = migrated[Keys.encryptedApiKey].orEmpty(),
                ditingTranscriptionModel = migrated[Keys.ditingTranscriptionModel] ?: "whisper-1",
                ditingTranscriptionEndpoint = migrated[Keys.ditingTranscriptionEndpoint].orEmpty(),
                ditingAiAnnotationEnabled = migrated[Keys.ditingAiAnnotationEnabled] ?: false,
                timetableColumnWidthDp = migrated[Keys.timetableColumnWidth] ?: 46,
                timetableRowHeightDp = migrated[Keys.timetableRowHeight] ?: 74,
                timetablePeriodCount = migrated[Keys.timetablePeriodCount] ?: 16,
                timetableWallpaperPath = migrated[Keys.timetableWallpaperPath].orEmpty(),
                timetableWallpaperAlpha = migrated[Keys.timetableWallpaperAlpha] ?: 0.28f,
                timetableShowEmptyCellsAlways = migrated[Keys.timetableShowEmptyCellsAlways] ?: true,
                accountEmail = migrated[Keys.accountEmail].orEmpty(),
                accountPasswordEncrypted = migrated[Keys.accountPassword].orEmpty(),
                accountLoggedIn = migrated[Keys.accountLoggedIn] ?: false,
            )
        }

    suspend fun updateGeneral(
        onboardingComplete: Boolean? = null,
        darkMode: Boolean? = null,
        followSystemTheme: Boolean? = null,
        courseReminderMinutes: Int? = null,
        taskReminderHoursFirst: Int? = null,
        taskReminderHoursSecond: Int? = null,
        notificationPermissionAsked: Boolean? = null,
    ) {
        context.settingsDataStore.edit { prefs ->
            onboardingComplete?.let { prefs[Keys.onboarding] = it }
            darkMode?.let { prefs[Keys.darkMode] = it }
            followSystemTheme?.let { prefs[Keys.followSystemTheme] = it }
            courseReminderMinutes?.let { prefs[Keys.courseReminder] = it.coerceIn(0, 240) }
            taskReminderHoursFirst?.let { prefs[Keys.taskReminderFirst] = it.coerceIn(0, 720) }
            taskReminderHoursSecond?.let { prefs[Keys.taskReminderSecond] = it.coerceIn(0, 168) }
            notificationPermissionAsked?.let { prefs[Keys.notificationPermissionAsked] = it }
        }
    }

    suspend fun updateThemeSeed(themeSeed: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.themeSeed] = themeSeed
        }
    }

    suspend fun saveAccount(email: String, encryptedPassword: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.accountEmail] = email
            prefs[Keys.accountPassword] = encryptedPassword
            prefs[Keys.accountLoggedIn] = true
        }
    }

    suspend fun setAccountLoggedIn(loggedIn: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.accountLoggedIn] = loggedIn
        }
    }

    suspend fun clearAccount() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.accountEmail)
            prefs.remove(Keys.accountPassword)
            prefs[Keys.accountLoggedIn] = false
        }
    }

    suspend fun updateAi(
        enabled: Boolean,
        provider: String,
        baseUrl: String,
        model: String,
        visionModel: String,
        authHeader: String,
        supportsVision: Boolean,
        encryptedApiKey: String,
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.aiEnabled] = enabled
            prefs[Keys.aiProvider] = provider.trim().ifBlank { "自定义" }
            prefs[Keys.aiBaseUrl] = baseUrl.trim().trimEnd('/')
            prefs[Keys.aiModel] = model.trim()
            prefs[Keys.aiVisionModel] = visionModel.trim()
            prefs[Keys.aiAuthHeader] = authHeader.trim().ifBlank { "Authorization: Bearer {key}" }
            prefs[Keys.aiSupportsVision] = supportsVision
            prefs[Keys.encryptedApiKey] = encryptedApiKey
        }
    }

    suspend fun updateDitingTranscription(model: String, endpoint: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ditingTranscriptionModel] = model.trim().ifBlank { "whisper-1" }
            prefs[Keys.ditingTranscriptionEndpoint] = endpoint.trim().trimEnd('/' )
        }
    }

    suspend fun updateDitingAiAnnotationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ditingAiAnnotationEnabled] = enabled
        }
    }

    suspend fun updateGradePreferences(scheme: String, customRules: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.gradeScheme] = scheme.takeIf { it in setOf("4.0", "4.3", "5.0", "自定义") } ?: "4.0"
            prefs[Keys.customGradeRules] = customRules.take(2_000)
        }
    }

    suspend fun updateTimetableLayout(columnWidthDp: Int, rowHeightDp: Int, periodCount: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.timetableColumnWidth] = columnWidthDp.coerceIn(38, 180)
            prefs[Keys.timetableRowHeight] = rowHeightDp.coerceIn(44, 220)
            prefs[Keys.timetablePeriodCount] = periodCount.coerceIn(6, 24)
        }
    }

    suspend fun updateTimetableWallpaper(path: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.timetableWallpaperPath] = path
        }
    }

    suspend fun updateTimetableBackgroundOptions(alpha: Float, showEmptyCellsAlways: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.timetableWallpaperAlpha] = alpha.coerceIn(0f, 1f)
            prefs[Keys.timetableShowEmptyCellsAlways] = showEmptyCellsAlways
        }
    }

    suspend fun restore(value: AppSettings) {
        updateGeneral(
            onboardingComplete = value.onboardingComplete,
            darkMode = value.darkMode,
            courseReminderMinutes = value.courseReminderMinutes,
            taskReminderHoursFirst = value.taskReminderHoursFirst,
            taskReminderHoursSecond = value.taskReminderHoursSecond,
            notificationPermissionAsked = value.notificationPermissionAsked,
        )
        updateAi(
            enabled = false,
            provider = value.aiProvider,
            baseUrl = value.aiBaseUrl,
            model = value.aiModel,
            visionModel = value.aiVisionModel,
            authHeader = value.aiAuthHeader,
            supportsVision = value.aiSupportsVision,
            encryptedApiKey = "",
        )
        updateDitingTranscription(value.ditingTranscriptionModel, value.ditingTranscriptionEndpoint)
        updateGradePreferences(
            scheme = value.gradeScheme,
            customRules = value.customGradeRules,
        )
        updateTimetableLayout(
            value.timetableColumnWidthDp,
            value.timetableRowHeightDp,
            value.timetablePeriodCount,
        )
        updateTimetableWallpaper(value.timetableWallpaperPath)
        updateTimetableBackgroundOptions(value.timetableWallpaperAlpha, value.timetableShowEmptyCellsAlways)
        // 账户凭据不随备份迁移：加密密钥绑定本机 Keystore，跨设备无法解密
        clearAccount()
    }
}
