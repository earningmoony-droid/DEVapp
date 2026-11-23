package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.HistoryStatusType
import com.example.model.TestHistoryEntry
import com.example.model.TesterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "webvisit_tester_store"
)

class TesterPreferencesRepository(private val context: Context) {

    private object Keys {
        val LAST_URL = stringPreferencesKey("last_url")
        val VIEW_DURATION_SEC = intPreferencesKey("view_duration_sec")
        val PAUSE_DURATION_SEC = intPreferencesKey("pause_duration_sec")
        val TOTAL_RUNS = intPreferencesKey("total_runs")
        val HISTORY_JSON = stringPreferencesKey("history_json")
        val TOTAL_COMPLETED_SESSIONS = intPreferencesKey("total_completed_sessions")
    }

    val settingsFlow: Flow<TesterSettings> = context.dataStore.data.map { prefs ->
        TesterSettings(
            lastUrl = prefs[Keys.LAST_URL] ?: "https://example.com",
            viewDurationSec = (prefs[Keys.VIEW_DURATION_SEC] ?: 15).coerceIn(3, 300),
            pauseDurationSec = (prefs[Keys.PAUSE_DURATION_SEC] ?: 3).coerceIn(0, 120),
            totalRuns = (prefs[Keys.TOTAL_RUNS] ?: 3).coerceIn(1, 50)
        )
    }

    val historyFlow: Flow<List<TestHistoryEntry>> = context.dataStore.data.map { prefs ->
        parseHistoryJson(prefs[Keys.HISTORY_JSON])
    }

    val lifetimeCompletedRunsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.TOTAL_COMPLETED_SESSIONS] ?: 0
    }

    suspend fun saveSettings(settings: TesterSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_URL] = settings.lastUrl
            prefs[Keys.VIEW_DURATION_SEC] = settings.viewDurationSec.coerceIn(3, 300)
            prefs[Keys.PAUSE_DURATION_SEC] = settings.pauseDurationSec.coerceIn(0, 120)
            prefs[Keys.TOTAL_RUNS] = settings.totalRuns.coerceIn(1, 50)
        }
    }

    suspend fun addHistoryEntry(entry: TestHistoryEntry, incrementCompletedRuns: Int = 0) {
        context.dataStore.edit { prefs ->
            val current = parseHistoryJson(prefs[Keys.HISTORY_JSON]).toMutableList()
            current.add(0, entry)
            val trimmed = current.take(MAX_HISTORY_ITEMS)
            prefs[Keys.HISTORY_JSON] = serializeHistoryJson(trimmed)
            if (incrementCompletedRuns > 0) {
                val existingCount = prefs[Keys.TOTAL_COMPLETED_SESSIONS] ?: 0
                prefs[Keys.TOTAL_COMPLETED_SESSIONS] = existingCount + incrementCompletedRuns
            }
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.HISTORY_JSON)
            prefs[Keys.TOTAL_COMPLETED_SESSIONS] = 0
        }
    }

    private fun parseHistoryJson(raw: String?): List<TestHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val statusName = obj.optString("statusType", HistoryStatusType.COMPLETED.name)
                    val statusType = runCatching { HistoryStatusType.valueOf(statusName) }
                        .getOrDefault(HistoryStatusType.COMPLETED)
                    add(
                        TestHistoryEntry(
                            id = obj.optString("id", ""),
                            timestampMillis = obj.optLong("timestampMillis", 0L),
                            url = obj.optString("url", ""),
                            statusType = statusType,
                            completedRuns = obj.optInt("completedRuns", 0),
                            totalRuns = obj.optInt("totalRuns", 1)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistoryJson(entries: List<TestHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("timestampMillis", entry.timestampMillis)
                put("url", entry.url)
                put("statusType", entry.statusType.name)
                put("completedRuns", entry.completedRuns)
                put("totalRuns", entry.totalRuns)
            }
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 200
    }
}
