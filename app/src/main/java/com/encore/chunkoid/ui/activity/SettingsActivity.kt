package com.encore.chunkoid.ui.activity

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.encore.chunkoid.R
import com.encore.chunkoid.databinding.ActivitySettingsBinding
import com.encore.chunkoid.utils.BackgroundUtils
import com.encore.chunkoid.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var themeExpanded = false
    private var customBgExpanded = false
    private var outputExpanded = false
    private var developerExpanded = false

    private val wallpaperPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processSelectedWallpaper(uri)
        }
    }

    companion object {
        const val CHUNKOID_OUTPUT_DIR = "chunkoid output"
        const val KEY_NIGHT_MODE = "night_mode"
        const val MODE_SYSTEM = "system"
        const val MODE_LIGHT = "light"
        const val MODE_DARK = "dark"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupSettings()
        setupCustomWallpaperSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            BackgroundUtils.applyCustomBackground(this)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }

    private fun setupSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val maxMemory = prefs.getInt("max_memory", 4096)
        binding.sliderMaxMemory.value = maxMemory.toFloat()
        binding.tvMemoryValue.text = "$maxMemory MB"

        binding.sliderMaxMemory.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val memoryValue = value.toInt()
                binding.tvMemoryValue.text = "$memoryValue MB"
                prefs.edit().putInt("max_memory", memoryValue).apply()
            }
        }

        binding.itemDimensionPruning.setOnClickListener {
            val intent = android.content.Intent(this, DimensionSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.switchWakeLock.isChecked = prefs.getBoolean("enable_wake_lock", true)
        binding.switchWakeLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_wake_lock", isChecked).apply()
        }

        binding.switchLowMemory.isChecked = prefs.getBoolean("low_memory_mode", false)
        binding.switchLowMemory.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("low_memory_mode", isChecked).apply()
        }

        binding.switchKeepNbt.isChecked = prefs.getBoolean("keep_original_nbt", false)
        binding.switchKeepNbt.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_original_nbt", isChecked).apply()
        }

        binding.itemThemeHeader.setOnClickListener {
            toggleTheme()
        }

        val currentMode = prefs.getString(KEY_NIGHT_MODE, MODE_SYSTEM)
        updateThemeChecks(currentMode)

        binding.themeOptionSystem.setOnClickListener {
            selectNightMode(MODE_SYSTEM)
        }
        binding.themeOptionLight.setOnClickListener {
            selectNightMode(MODE_LIGHT)
        }
        binding.themeOptionDark.setOnClickListener {
            selectNightMode(MODE_DARK)
        }

        val isDynamicColorSupported = com.google.android.material.color.DynamicColors.isDynamicColorAvailable()
        binding.switchDynamicColor.isEnabled = isDynamicColorSupported
        binding.switchDynamicColor.isChecked = isDynamicColorSupported && prefs.getBoolean("dynamic_color", false)
        binding.switchDynamicColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dynamic_color", isChecked).apply()
            ToastUtils.show(this, "动态取色设置已更新，重启应用生效")
        }

        binding.itemOutputHeader.setOnClickListener {
            toggleOutput()
        }
        binding.btnClearOutput.setOnClickListener {
            clearOutput()
        }

        binding.itemDeveloperHeader.setOnClickListener {
            toggleDeveloper()
        }

        binding.switchShowUndeveloped.isChecked = prefs.getBoolean("show_undeveloped", false)
        binding.switchShowUndeveloped.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_undeveloped", isChecked).apply()
        }

        binding.switchShowTerminal.isChecked = prefs.getBoolean("show_terminal", true)
        binding.switchShowTerminal.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_terminal", isChecked).apply()
        }

        binding.switchEnableLogOutput.isChecked = prefs.getBoolean("enable_conversion_log", false)
        binding.switchEnableLogOutput.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_conversion_log", isChecked).apply()
        }
    }

    private fun setupCustomWallpaperSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        binding.itemCustomWallpaperHeader.setOnClickListener {
            toggleCustomWallpaper()
        }

        val isBgEnabled = prefs.getBoolean(BackgroundUtils.KEY_CUSTOM_BG_ENABLED, false)
        binding.switchCustomWallpaper.isChecked = isBgEnabled

        binding.switchCustomWallpaper.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !BackgroundUtils.getWallpaperFile(this).exists()) {
                wallpaperPickerLauncher.launch("image/*")
            } else {
                prefs.edit().putBoolean(BackgroundUtils.KEY_CUSTOM_BG_ENABLED, isChecked).apply()
                recreate()
            }
        }

        binding.btnPickWallpaper.setOnClickListener {
            wallpaperPickerLauncher.launch("image/*")
        }

        binding.switchExtractBgColor.isChecked = prefs.getBoolean(BackgroundUtils.KEY_EXTRACT_COLOR, true)
        binding.switchExtractBgColor.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(BackgroundUtils.KEY_EXTRACT_COLOR, isChecked).apply()
            recreate()
        }

        val dimPercent = prefs.getInt(BackgroundUtils.KEY_BG_DIM, 25)
        binding.sliderBgDim.value = dimPercent.toFloat()
        binding.tvDimValue.text = "$dimPercent%"

        binding.sliderBgDim.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intVal = value.toInt()
                binding.tvDimValue.text = "$intVal%"
                prefs.edit().putInt(BackgroundUtils.KEY_BG_DIM, intVal).apply()
            }
        }

        binding.sliderBgDim.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                if (BackgroundUtils.isCustomBackgroundEnabled(this@SettingsActivity)) {
                    recreate()
                }
            }
        })

        binding.btnClearWallpaper.setOnClickListener {
            BackgroundUtils.clearCustomWallpaper(this)
            updateWallpaperPreview()
            binding.switchCustomWallpaper.isChecked = false
            ToastUtils.show(this, getString(R.string.wallpaper_cleared_success))
            recreate()
        }

        updateWallpaperPreview()
    }

    private fun processSelectedWallpaper(uri: Uri) {
        lifecycleScope.launch {
            ToastUtils.show(this@SettingsActivity, "正在处理壁纸与提取主题色彩...")
            val success = BackgroundUtils.saveCustomWallpaper(this@SettingsActivity, uri)
            if (success) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity)
                prefs.edit().putBoolean(BackgroundUtils.KEY_CUSTOM_BG_ENABLED, true).apply()
                binding.switchCustomWallpaper.isChecked = true
                updateWallpaperPreview()
                ToastUtils.show(this@SettingsActivity, getString(R.string.wallpaper_set_success))
                recreate()
            } else {
                ToastUtils.show(this@SettingsActivity, "壁纸处理失败，请重试")
            }
        }
    }

    private fun updateWallpaperPreview() {
        val wallpaperFile = BackgroundUtils.getWallpaperFile(this)
        if (wallpaperFile.exists()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = try {
                    BitmapFactory.decodeFile(wallpaperFile.absolutePath)
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (bitmap != null && !isFinishing && !isDestroyed) {
                        binding.ivWallpaperPreview.setImageBitmap(bitmap)
                        binding.tvWallpaperFilename.text = "已应用自定义壁纸 (${bitmap.width}x${bitmap.height})"
                        binding.btnClearWallpaper.visibility = View.VISIBLE
                    } else if (bitmap == null) {
                        binding.ivWallpaperPreview.setImageResource(R.drawable.ic_texture)
                        binding.tvWallpaperFilename.text = getString(R.string.select_wallpaper_desc)
                        binding.btnClearWallpaper.visibility = View.GONE
                    }
                }
            }
        } else {
            binding.ivWallpaperPreview.setImageResource(R.drawable.ic_texture)
            binding.tvWallpaperFilename.text = getString(R.string.select_wallpaper_desc)
            binding.btnClearWallpaper.visibility = View.GONE
        }
    }

    private fun toggleTheme() {
        themeExpanded = !themeExpanded
        if (themeExpanded) {
            binding.layoutThemeContent.visibility = View.VISIBLE
            rotateArrow(binding.ivThemeArrow, true)
        } else {
            binding.layoutThemeContent.visibility = View.GONE
            rotateArrow(binding.ivThemeArrow, false)
        }
    }

    private fun toggleCustomWallpaper() {
        customBgExpanded = !customBgExpanded
        if (customBgExpanded) {
            binding.layoutCustomWallpaperContent.visibility = View.VISIBLE
            rotateArrow(binding.ivCustomWallpaperArrow, true)
        } else {
            binding.layoutCustomWallpaperContent.visibility = View.GONE
            rotateArrow(binding.ivCustomWallpaperArrow, false)
        }
    }

    private fun toggleOutput() {
        outputExpanded = !outputExpanded
        if (outputExpanded) {
            binding.layoutOutputContent.visibility = View.VISIBLE
            rotateArrow(binding.ivOutputArrow, true)
        } else {
            binding.layoutOutputContent.visibility = View.GONE
            rotateArrow(binding.ivOutputArrow, false)
        }
    }

    private fun toggleDeveloper() {
        developerExpanded = !developerExpanded
        if (developerExpanded) {
            binding.layoutDeveloperContent.visibility = View.VISIBLE
            rotateArrow(binding.ivDeveloperArrow, true)
        } else {
            binding.layoutDeveloperContent.visibility = View.GONE
            rotateArrow(binding.ivDeveloperArrow, false)
        }
    }

    private fun rotateArrow(imageView: ImageView, expanded: Boolean) {
        val fromDegrees = if (expanded) 0f else 180f
        val toDegrees = if (expanded) 180f else 0f
        val rotateAnimation = RotateAnimation(
            fromDegrees, toDegrees,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            fillAfter = true
        }
        imageView.startAnimation(rotateAnimation)
    }

    private fun clearOutput() {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val chunkoidOutputDir = File(documentsDir, CHUNKOID_OUTPUT_DIR)

        if (!chunkoidOutputDir.exists()) {
            ToastUtils.show(this, getString(R.string.output_cleared))
            return
        }

        var count = 0
        chunkoidOutputDir.listFiles()?.forEach { worldDir ->
            if (worldDir.isDirectory) {
                if (worldDir.deleteRecursively()) {
                    count++
                }
            }
        }

        ToastUtils.show(this, getString(R.string.output_cleared) + " ($count)")
    }

    private fun selectNightMode(mode: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString(KEY_NIGHT_MODE, mode).apply()
        updateThemeChecks(mode)

        when (mode) {
            MODE_LIGHT -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
            else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        ToastUtils.show(this, getString(R.string.theme_changed))
    }

    private fun updateThemeChecks(selectedMode: String?) {
        val mode = selectedMode ?: MODE_SYSTEM
        binding.themeCheckSystem.visibility = if (mode == MODE_SYSTEM) View.VISIBLE else View.GONE
        binding.themeCheckLight.visibility = if (mode == MODE_LIGHT) View.VISIBLE else View.GONE
        binding.themeCheckDark.visibility = if (mode == MODE_DARK) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun applyNoElevationToCards() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        removeCardElevation(rootView)
    }

    private fun removeCardElevation(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.cardElevation = 0f
            } else if (child is ViewGroup) {
                removeCardElevation(child)
            }
        }
    }
}