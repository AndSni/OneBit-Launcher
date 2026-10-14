package com.asnidev.onebit.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt settings file is replaced by defaults: a launcher that can't read its
// settings must still start, or the phone has no home screen.
private val Context.dataStore by preferencesDataStore(
    name = "onebit",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.w("SettingsStore", "settings file was corrupt; starting from defaults", it)
        emptyPreferences()
    },
)

class SettingsStore(private val context: Context) {

    private val key = stringPreferencesKey("prefs")

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e !is IOException) throw e
        Log.w("SettingsStore", "couldn't read settings", e)
        emit(emptyPreferences())
    }

    val prefs: Flow<Prefs> = data.map { PrefsJson.read(it[key]) }

    suspend fun update(transform: (Prefs) -> Prefs) {
        context.dataStore.edit { p -> p[key] = PrefsJson.write(transform(PrefsJson.read(p[key]))) }
    }
}
