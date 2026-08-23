package com.encore.chunkoid

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors

class ChunkoidApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Apply Material Design 3 Dynamic Colors (Material You) if enabled by user
        if (prefs.getBoolean("dynamic_color", false)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        // Apply saved dark/light theme mode
        when (prefs.getString("night_mode", "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}

