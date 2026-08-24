package com.encore.chunkoid.ui.activity

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.encore.chunkoid.databinding.ActivityDimensionSettingsBinding
import com.encore.chunkoid.utils.BackgroundUtils

class DimensionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDimensionSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDimensionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        binding.switchKeepNether.isChecked = prefs.getBoolean("keep_nether", true)
        binding.switchKeepEnd.isChecked = prefs.getBoolean("keep_end", true)

        binding.switchKeepNether.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_nether", isChecked).apply()
        }

        binding.switchKeepEnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_end", isChecked).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        BackgroundUtils.applyCustomBackground(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}