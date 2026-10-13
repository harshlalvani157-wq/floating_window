package com.freefloat.workspace.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.workspaceDataStore by preferencesDataStore("workspace_preferences")
class WorkspaceRepository(private val context: Context) {
    private val darkKey = booleanPreferencesKey("dark_theme")
    private val opacityKey = floatPreferencesKey("default_opacity")
    val darkTheme: Flow<Boolean> = context.workspaceDataStore.data.map { it[darkKey] ?: false }
    val defaultOpacity: Flow<Float> = context.workspaceDataStore.data.map { it[opacityKey] ?: 1f }
}
