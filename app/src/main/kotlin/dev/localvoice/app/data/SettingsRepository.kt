package dev.localvoice.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.localvoice.core.LanguageMode
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("preferences")
data class UserSettings(
    val language: LanguageMode = LanguageMode.MIXED,
    val modelId: String = "whisper-base", val theme: String = "light",
    val autoHistory: Boolean = false, val retainAudio: Boolean = false,
    val bubble: Boolean = false, val accessibilityConsent: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    val bubbleX: Float = 1f, val bubbleY: Float = .45f,
)
class SettingsRepository(private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val model = stringPreferencesKey("model")
    private val theme = stringPreferencesKey("theme")
    private val autoHistory = booleanPreferencesKey("auto_history")
    private val retainAudio = booleanPreferencesKey("retain_audio")
    private val bubble = booleanPreferencesKey("bubble")
    private val consent = booleanPreferencesKey("accessibility_consent_v1")
    private val excluded = stringSetPreferencesKey("excluded_apps")
    private val x = floatPreferencesKey("bubble_x")
    private val y = floatPreferencesKey("bubble_y")
    val data = context.preferences.data.map { p -> UserSettings(
        language = runCatching { LanguageMode.valueOf(p[language].orEmpty()) }.getOrDefault(LanguageMode.MIXED),
        modelId = p[model] ?: "whisper-base", theme = p[theme] ?: "light",
        autoHistory = p[autoHistory] ?: false, retainAudio = p[retainAudio] ?: false,
        bubble = p[bubble] ?: false, accessibilityConsent = p[consent] ?: false,
        excludedApps = p[excluded] ?: emptySet(), bubbleX = p[x] ?: 1f, bubbleY = p[y] ?: .45f,
    ) }
    suspend fun language(value: LanguageMode) { context.preferences.edit { it[language] = value.name } }
    suspend fun model(value: String) { context.preferences.edit { it[model] = value } }
    suspend fun theme(value: String) { require(value in listOf("light","dark","system")); context.preferences.edit { it[theme] = value } }
    suspend fun history(value: Boolean) { context.preferences.edit { it[autoHistory] = value; if(!value) it[retainAudio] = false } }
    suspend fun audio(value: Boolean) { context.preferences.edit { it[retainAudio] = value; if(value) it[autoHistory] = true } }
    suspend fun bubble(value: Boolean) { context.preferences.edit { it[bubble] = value } }
    suspend fun consent() { context.preferences.edit { it[consent] = true } }
    suspend fun exclude(packageName: String) { context.preferences.edit { it[excluded] = it[excluded].orEmpty() + packageName } }
    suspend fun include(packageName: String) { context.preferences.edit { it[excluded] = it[excluded].orEmpty() - packageName } }
    suspend fun bubblePosition(a: Float,b: Float) { context.preferences.edit { it[x] = a.coerceIn(0f,1f); it[y] = b.coerceIn(0f,1f) } }
}
